package aug.script

import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}

import aug.profile._
import aug.script.shared.{ClientInterface, ProfileInterface}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object ScriptLoader {
  val log = Logger(LoggerFactory.getLogger(ScriptLoader.getClass))

  private val clientInterfaceT = classOf[ClientInterface]

  def constructScript(profileConfig: ProfileConfig): Script = {
    val classpath = profileConfig.javaConfig.classPath.map(new File(_).toURI.toURL)
    val mainClass = profileConfig.javaConfig.mainClass
    val scriptLoader = new ScriptLoader(classpath)
    val clientT = scriptLoader.loadClass(mainClass)
    if (!clientInterfaceT.isAssignableFrom(clientT)) {
      throw MainClassNotClientInterface
    }

    val client = clientT.newInstance().asInstanceOf[ClientInterface]
    new Script(profileConfig, client)
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

sealed trait ScriptEventType

case object OnConnectScriptEventType extends ScriptEventType
case object OnDisconnectScriptEventType extends ScriptEventType

case class InitScriptEventType(profile: ProfileInterface) extends ScriptEventType
case class HandleLineScriptEventType(line: String) extends ScriptEventType
case class HandleFragmentScriptEventType(line: String) extends ScriptEventType
case class HandleGmcpScriptEventType(line: String) extends ScriptEventType
case class HandleCommandScriptEventType(cmd: String) extends ScriptEventType

object OutOfTimeException extends RuntimeException("script did not return in time")

class Script private[script] (profileConfig: ProfileConfig, client: ClientInterface) extends AutoCloseable
  with ClientInterface {
  import ScriptLoader.log
  import Util.Implicits._

  private val thread = new Thread(loop(), "Script : " + profileConfig.name)
  private val running = new AtomicBoolean(true)
  private var startLatch = new CountDownLatch(1)
  private var finishLatch = new CountDownLatch(1)
  private var scriptEventType : ScriptEventType = OnConnectScriptEventType

  thread.start()

  private def loop() : Unit = {
    while(running.get) {
      startLatch.await()

      if (!thread.isInterrupted) {
        Try {
          scriptEventType match {
            case InitScriptEventType(profile) => client.init(profile)
            case OnConnectScriptEventType => client.onConnect()
            case OnDisconnectScriptEventType => client.onDisconnect()
            case HandleLineScriptEventType(line) => client.handleLine(line)
            case HandleFragmentScriptEventType(line) => client.handleFragment(line)
            case HandleGmcpScriptEventType(line) => client.handleGmcp(line)
            case HandleCommandScriptEventType(cmd) => client.handleCommand(cmd)
          }
        } match {
          case Failure(e) =>
            log.error("caught script exception", e)
          case _ =>
        }

        val currentFinishLatch = finishLatch
        finishLatch = new CountDownLatch(1)
        startLatch = new CountDownLatch(1)
        currentFinishLatch.countDown()
      }
    }

    client.shutdown()
  }

  override def close(): Unit = {
    if (running.compareAndSet(true, false)) {
      thread.interrupt()
      thread.join(1000)
    }
  }

  override def shutdown(): Unit = close()

  private def execute(scriptEventType: ScriptEventType): Unit = {
    val finishLatch = this.finishLatch
    this.scriptEventType = scriptEventType
    startLatch.countDown()
    if(!finishLatch.await(1000, TimeUnit.MILLISECONDS)) {
      throw OutOfTimeException
    }
  }

  override def init(profile: ProfileInterface): Unit = execute(InitScriptEventType(profile))

  override def onConnect(): Unit = execute(OnConnectScriptEventType)

  override def handleLine(s: String): Unit = execute(HandleLineScriptEventType(s))

  override def handleFragment(s: String): Unit = execute(HandleFragmentScriptEventType(s))

  override def onDisconnect(): Unit = execute(OnDisconnectScriptEventType)

  override def handleGmcp(s: String): Unit = execute(HandleGmcpScriptEventType(s))

  override def handleCommand(s: String): Unit = execute(HandleCommandScriptEventType(s))
}