package aug.script

import java.awt.Color
import java.io.{File, PrintWriter, StringWriter}
import java.net.{URL, URLClassLoader}
import java.nio.ByteBuffer

import aug.profile._
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

object ScriptLoader {
  val log = Logger(LoggerFactory.getLogger(ScriptLoader.getClass))

  val sharedClasses = Set(
    classOf[ProfileEventListener],
    classOf[ProfileEvent],
    TelnetConnect.getClass

  ) map { _.getCanonicalName }

  def classpath: Array[URL] = {
    val classpath: String = System.getProperty("java.class.path")
    val urls = Seq.newBuilder[URL]
    for (dir <- classpath.split(":")) yield {
      log.trace("Adding classpath URL {}", dir)
      urls += new File(dir.replaceAll("\\;C","")).toURI.toURL
    }

    urls.result.toArray
  }

  private def loadScript(className: String, scriptLoader: ScriptLoader, profile: Profile) : ProfileEventListener = {

    def loadBasicScript = scriptLoader.loadClass(classOf[BasicScript].getCanonicalName).newInstance().asInstanceOf[ProfileEventListener]

    if(className.isEmpty) loadBasicScript else {
      Try {
        log.info("loading script class {}", className)
        scriptLoader.loadClass(className).newInstance().asInstanceOf[ProfileEventListener]
      } match {
        case Success(c) => c
        case Failure(e) =>
          profile.info(s"ERROR: cannot load script $className")
          loadBasicScript
      }
    }
  }

  def newScript(className: String, scriptDir: File, profile: Profile) = {
    val scriptLoader = new ScriptLoader(classpath :+ scriptDir.toURI.toURL)

    val script = loadScript(className,scriptLoader,profile)

    val srType = scriptLoader.loadClass(classOf[ScriptRunner].getCanonicalName)
    val scriptRunner = srType.getConstructor(classOf[ProfileInterface],classOf[ProfileEventListener])
      .newInstance(profile,script).asInstanceOf[ProfileEventListener]

    scriptRunner
  }
}

private class ScriptLoader(val urls: Array[URL]) extends ClassLoader(Thread.currentThread().getContextClassLoader) {

  import ScriptLoader._

  private class DetectClass(val parent: ClassLoader) extends ClassLoader(parent) {
    override def findClass(name: String) = super.findClass(name)
  }

  private class ChildClassLoader(val urls: Array[URL], realParent: DetectClass) extends URLClassLoader(urls,null) {

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
            realParent.loadClass(name)
          case Success(c) => c
        }
      }
    }

    def deferToParent(name:String) : Boolean = {
      name.startsWith("aug.profile.") ||
      name.startsWith("java") ||
      name.startsWith("scala")
    }
  }

  private val childClassLoader = new ChildClassLoader(urls,new DetectClass(getParent))

  override protected def loadClass(name: String, resolve: Boolean) : Class[_] = {
    Try {
      childClassLoader.findClass(name)
    } match {
      case Failure(e) => super.loadClass(name,resolve)
      case Success(c) => c
    }
  }

}

class ScriptRunner (val profile: ProfileInterface, val script: ProfileEventListener) extends ProfileEventListener {

  private val buffer = ByteBuffer.allocate(2<<20)
  private var pos = 0
  private val lastColor = "[0"


  Game.profile = Some(profile)
  dispatch(ScriptInit)


  private def dispatch(event: ProfileEvent, data: Option[String] = None): Unit = {
    Try {
      script.event(event,data)
    } match {
      case Failure(e)=> Game.handleException(e)
      case _ =>
    }
  }
  override def event(event: ProfileEvent, data: Option[String]): Unit = {
    event match {
      case TelnetConnect => profile.echo("--connected--\n",Some(Color.YELLOW))
      case TelnetDisconnect => profile.echo("--disconnected--\n",Some(Color.YELLOW))
      case TelnetRecv => data map { s=> recv(s)}
      case ScriptClose => Game.close
      case UserCommand => data map { s => Alias.processAliases(s) }
      case _ =>
    }

    dispatch(event,data)
  }

  private def handleLine(): Unit = {
    val newLine = new String(buffer.array(),0,buffer.position())
    val noColors = Util.removeColors(newLine)

    Trigger.processTriggers(noColors)

    if(pos != 0)
      profile.addColoredText(newLine.substring(pos))
    else
      profile.addColoredText(newLine)

    dispatch(ScriptNewLine, Some(noColors))
    dispatch(ScriptNewColorLine,Some(newLine))

    buffer.clear
    buffer.put(27.toByte)
    buffer.put(lastColor.getBytes)
    buffer.put('m'.toByte)
  }

  private def handleFragment() : Unit = {
    val fragment = new String(buffer.array(),0,buffer.position())
    val noColors = Util.removeColors(fragment)

    Trigger.processFragmentTriggers(noColors)

    profile.addColoredText(fragment)

    dispatch(ScriptFragment,Some(noColors))
    dispatch(ScriptColorFragment,Some(fragment))
  }

  private def recv(txt: String) = {
    val bytes = txt.getBytes

    for(b <- bytes) {
      buffer.put(b)
      if(b=='\n') {
        handleLine()
      }
    }

    handleFragment()
  }
}

class BasicScript extends ProfileEventListener {
  override def event(event: ProfileEvent, data: Option[String]): Unit = {
    event match {
      case _ =>
    }
  }
}

object Game extends ProfileInterface {

  private[script] var profile : Option[ProfileInterface] = None
  val log = Logger(LoggerFactory.getLogger(Game.getClass))

  def handleException(t: Throwable) : Unit = {
    val sw = new StringWriter()
    t.printStackTrace(new PrintWriter(sw))
    log.error("Game exception",t)
    echo(sw.toString + "\n")

  }

  def close : Unit = {

  }

  override def info(s: String, window: String = defaultWindow): Unit = profile map { _.info(s,window) }
  override def addColoredText(s: String, window: String = defaultWindow): Unit = profile map { _.addColoredText(s,window) }
  override def echo(s: String, color: Option[Color], window: String = defaultWindow): Unit = profile map { _.echo(s,color,window) }
  override def consumeNextCommand(): Unit = profile map { _.consumeNextCommand() }
  override def send(s: String): Unit = profile map (_.send(s))
}