package aug.script

import java.io.File
import java.lang.management.{ManagementFactory, ThreadMXBean}
import java.net.{URL, URLClassLoader}
import java.util.concurrent.{Callable, Executors, TimeUnit}

import aug.profile._
import aug.script.framework._
import com.typesafe.scalalogging.Logger
import org.mongodb.scala.{MongoClient, MongoDatabase}
import org.slf4j.LoggerFactory

import scala.concurrent.TimeoutException
import scala.util.{Failure, Success, Try}

object ScriptLoader {
  val log = Logger(LoggerFactory.getLogger(ScriptLoader.getClass))
  val FRAMEWORK_CLASSPATH: String = classOf[ClientInterface].getPackage.getName
  lazy val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean

  private val clientInterfaceT = classOf[ClientInterface]

  def constructScript(profile: Profile, profileConfig: ProfileConfig): Client = {
    val classpath: Array[URL] = profileConfig.javaConfig.classPath.map(new File(_).toURI.toURL)
    val mainClass = profileConfig.javaConfig.mainClass

    log.info(s"will try to load $mainClass from classpath ${classpath.toList}")

    val scriptLoader = new ScriptLoader(classpath)
    val clientT = scriptLoader.loadClass(mainClass)
    if (!clientInterfaceT.isAssignableFrom(clientT)) {
      throw MainClassNotClientInterface
    }

    val client = clientT.newInstance().asInstanceOf[ClientInterface]

    new Client(profile, profileConfig, client)
  }
}

object MainClassNotClientInterface extends RuntimeException("main class doesn't extend client class")
case class ClientTimeoutException(tinfo: String) extends RuntimeException("client timed out")

private class ScriptLoader(val urls: Array[URL]) extends ClassLoader(Thread.currentThread().getContextClassLoader) {

  import ScriptLoader._

  private class DetectClass(val parent: ClassLoader) extends ClassLoader(parent) {
    override def findClass(name: String): Class[_] = super.findClass(name)
  }

  private class ChildClassLoader(val urls: Array[URL], realParent: DetectClass) extends URLClassLoader(urls, null) {

    override def findClass(name: String): Class[_] = {

      if(deferToParent(name)) {
        log.trace("FREE: {}",name)
        realParent.loadClass(name)
      } else {

        Try {
          log.trace("JAILED {}", name)

          Option(super.findLoadedClass(name)) getOrElse {
            super.findClass(name)
          }
        } match {
          case Failure(e) =>
            log.error(s"failed to load in jail $name")
            throw new ClassNotFoundException()
          case Success(c) => c
        }
      }
    }

    val parentPrefixes = List(
      ScriptLoader.FRAMEWORK_CLASSPATH,
      "aug.script.examples",
      "java",
      "scala",
      "org.mongodb"
    )

    def deferToParent(name: String) : Boolean = parentPrefixes.exists(name.startsWith)
  }

  private val childClassLoader = new ChildClassLoader(urls, new DetectClass(getParent))

  override protected def loadClass(name: String, resolve: Boolean) : Class[_] = {
    Try {
      childClassLoader.findClass(name)
    } match {
      case Failure(e) =>
        throw new Exception(s"failed to load class $name with classpath ${urls.toList}", e)
      case Success(c) => c
    }
  }

}

/**
  * <p>
  *   Client wrapper class that uses a thread pool to prevent tying up the profile event thread while calling client
  *   methods more than the configured timeout.  If the timeout is reached, the client thread will be interrupted and
  *   the client can expect to be shutdown quickly after that.
  * </p>
  */
class Client private[script](profile: Profile, profileConfig: ProfileConfig, client: ClientInterface) extends AutoCloseable
  with ClientInterface {
  import ScriptLoader.log

  private var threadId: Option[Long] = None
  private val executorService = Executors.newFixedThreadPool(1)
  private var scheduler: Option[Scheduler] = None
  private var inError: Boolean = false

  executorService.submit(new Runnable {
    override def run(): Unit = {
      threadId = Some(Thread.currentThread().getId)
    }
  })

  def getScheduler(state: List[String], reloaders: Seq[RunnableReloader[_ <: Runnable]]): Scheduler = {
    scheduler.getOrElse {
      val sch = new Scheduler(this, profile, reloaders)
      sch.hydrate(state)
      scheduler = Some(sch)
      sch
    }
  }

  def schedulerState: List[String] = scheduler.map(_.save).getOrElse(List.empty)

  override def close(): Unit = {
    executorService.shutdownNow()
    if (!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
      log.error("failed to shutdown client executor service")
    }
    scheduler.foreach(_.close())
  }

  private def threadInfo: String = {
    threadId.flatMap(id => Option(ScriptLoader.threadMXBean.getThreadInfo(id, 20)))
      .map(_.toString)
      .getOrElse("unable to acquire thread dump")
  }

  override def shutdown(): ReloadData = {

    val m: ReloadData = try {
      executeOnThread(client.shutdown(), cancelOnTimeout = false)
    } catch {
      case e: TimeoutException =>
        profile.slog.error(s"client timed out while shutting down (very bad!)\n$threadInfo")
        new ReloadData
      case e: Throwable =>
        profile.handleClientException(e)
        new ReloadData
    }

    close()
    if (m == null) new ReloadData else m
  }

  private def execute[ReturnType](f: => ReturnType): ReturnType = {
    if (inError) throw new RuntimeException("client is in error")

    executeOnThread(f, cancelOnTimeout = true)
  }

  private def executeOnThread[ReturnType](f: => ReturnType, cancelOnTimeout: Boolean): ReturnType = {
    val future = executorService.submit(new Callable[ReturnType] {
      override def call(): ReturnType = f
    })

    Try {
      future.get(profileConfig.javaConfig.clientTimeout, TimeUnit.MILLISECONDS)
    } match {
      case Success(rv) => rv
      case Failure(e: TimeoutException) =>
        val to = ClientTimeoutException(threadInfo)
        if (cancelOnTimeout) future.cancel(true)
        inError = true
        throw e

      case Failure(e) =>
        profile.handleClientException(e)
        inError = true
        throw e
    }
  }

  def handleEvent(runnable: Runnable): Unit = execute(runnable.run())
  override def init(profile: ProfileInterface, reloadData: ReloadData): Unit = execute(client.init(profile, reloadData))
  override def onConnect(id: Long, url: String, port: Int): Unit = execute(client.onConnect(id, url, port))
  override def handleLine(lineEvent: LineEvent): Boolean = execute(client.handleLine(lineEvent))
  override def handleFragment(lineEvent: LineEvent): Unit = execute(client.handleFragment(lineEvent))
  override def onDisconnect(id: Long): Unit = execute(client.onDisconnect(id))
  override def handleGmcp(s: String): Unit = execute(client.handleGmcp(s))
  override def handleCommand(s: String): Boolean = execute(client.handleCommand(s))
  override def initDB(mc: MongoClient, md: MongoDatabase): Unit = execute(client.initDB(mc, md))
}