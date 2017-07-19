package aug.script

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}

import aug.profile._
import aug.script.shared.{ClientInterface, ProfileInterface}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.TimeoutException
import scala.util.{Failure, Success, Try}

object ScriptLoader {
  val log = Logger(LoggerFactory.getLogger(ScriptLoader.getClass))

  private val clientInterfaceT = classOf[ClientInterface]

  def constructScript(profileConfig: ProfileConfig): Client = {
    val classpath = profileConfig.javaConfig.classPath.map(new File(_).toURI.toURL)
    val mainClass = profileConfig.javaConfig.mainClass
    val scriptLoader = new ScriptLoader(classpath)
    val clientT = scriptLoader.loadClass(mainClass)
    if (!clientInterfaceT.isAssignableFrom(clientT)) {
      throw MainClassNotClientInterface
    }

    val client = clientT.newInstance().asInstanceOf[ClientInterface]
    new Client(profileConfig, client)
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
            log.error(s"failed to load in jail $name", e)
            throw new ClassNotFoundException()
          case Success(c) => c
        }
      }
    }

    def deferToParent(name:String) : Boolean = {
      name.startsWith("aug.script.shared") ||
      name.startsWith("java") ||
      name.startsWith("scala")
    }
  }

  private val childClassLoader = new ChildClassLoader(urls, new DetectClass(getParent))

  override protected def loadClass(name: String, resolve: Boolean) : Class[_] = {
    Try {
      childClassLoader.findClass(name)
    } match {
      case Failure(e) => throw e
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
class Client private[script](profileConfig: ProfileConfig, client: ClientInterface) extends AutoCloseable
  with ClientInterface {
  import ScriptLoader.log

  private val executorService = Executors.newFixedThreadPool(1)

  override def close(): Unit = {
    executorService.shutdownNow()
    if(!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
      log.error("failed to shutdown client executor service")
    }
  }

  override def shutdown(): Unit = close()

  private def execute[ReturnType](f: () => ReturnType): ReturnType = {
    val future = executorService.submit(new Callable[ReturnType] {
      override def call(): ReturnType = f()
    })

    Try {
      future.get(500, TimeUnit.MILLISECONDS)
    } match {
      case Success(rv) => rv
      case Failure(e: TimeoutException) =>
        future.cancel(true)
        throw e
      case Failure(e) => throw e
    }
  }

  override def init(profile: ProfileInterface): Unit = execute(() => client.init(profile))
  override def onConnect(): Unit = execute(() => client.onConnect())
  override def handleLine(s: String): Unit = execute(() => client.handleLine(s))
  override def handleFragment(s: String): Unit = execute(() => client.handleFragment(s))
  override def onDisconnect(): Unit = execute(() => client.onDisconnect())
  override def handleGmcp(s: String): Unit = execute(() => client.handleGmcp(s))
  override def handleCommand(s: String): Unit = execute(() => client.handleCommand(s))
}