package aug.script

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}

import aug.profile._
import aug.script.shared.{ClientInterface, ProfileInterface, ReloadData}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.TimeoutException
import scala.util.{Failure, Success, Try}

object ScriptLoader {
  val log = Logger(LoggerFactory.getLogger(ScriptLoader.getClass))

  private val clientInterfaceT = classOf[ClientInterface]

  def constructScript(profile: Profile, profileConfig: ProfileConfig): Client = {
    val classpath = profileConfig.javaConfig.classPath.map(new File(_).toURI.toURL)
    val mainClass = profileConfig.javaConfig.mainClass
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

private class ScriptLoader(val urls: Array[URL]) extends ClassLoader(Thread.currentThread().getContextClassLoader) {

  import ScriptLoader._

  private class DetectClass(val parent: ClassLoader) extends ClassLoader(parent) {
    override def findClass(name: String) = super.findClass(name)
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

    val parentPrefixes = List("aug.script.shared", "aug.script.test", "java", "scala")

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
  * @param profileConfig
  * @param client
  */
class Client private[script](profile: Profile, profileConfig: ProfileConfig, client: ClientInterface) extends AutoCloseable
  with ClientInterface {
  import ScriptLoader.log

  private val executorService = Executors.newFixedThreadPool(1)

  override def close(): Unit = {
    executorService.shutdownNow()
    if(!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
      log.error("failed to shutdown client executor service")
    }
  }

  override def shutdown(): ReloadData = {

    val m = Try {
      execute(() => client.shutdown())
    } match {
      case Failure(e) =>
        profile.handleClientException(e)
        new ReloadData
      case Success(m) => if (m == null) new ReloadData else m
    }

    close()
    m
  }

  private def execute[ReturnType](f: () => ReturnType): ReturnType = {
    val future = executorService.submit(new Callable[ReturnType] {
      override def call(): ReturnType = f()
    })

    Try {
      future.get(profileConfig.javaConfig.clientTimeout, TimeUnit.MILLISECONDS)
    } match {
      case Success(rv) => rv
      case Failure(e: TimeoutException) =>
        future.cancel(true)
        throw e
      case Failure(e) =>
        profile.handleClientException(e)
        throw e
    }
  }

  override def init(profile: ProfileInterface, reloadData: ReloadData): Unit =
    execute(() => client.init(profile, reloadData))
  override def onConnect(): Unit = execute(() => client.onConnect())
  override def handleLine(lineNum: Long, line: String): Boolean = execute(() => client.handleLine(lineNum, line))
  override def handleFragment(s: String): Unit = execute(() => client.handleFragment(s))
  override def onDisconnect(): Unit = execute(() => client.onDisconnect())
  override def handleGmcp(s: String): Unit = execute(() => client.handleGmcp(s))
  override def handleCommand(s: String): Boolean = execute(() => client.handleCommand(s))
}