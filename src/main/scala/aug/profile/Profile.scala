package aug.profile

import java.awt.event.KeyEvent
import java.awt.{BorderLayout, Color, EventQueue}
import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing.{JPanel, JTabbedPane, UIManager}

import aug.gui.{CommandLine, CommandPane, MainTabbedPane, MainWindow, SplitTextPanel, TextPanel, TextReceiver}
import aug.io.{ProfileEvent, ProfileEventListener, Telnet, TelnetConnect, TelnetDisconnect, TelnetError, TelnetRecv}
import aug.script.ScriptManager
import aug.util.{TryWith, Util}
import com.google.common.base.Splitter
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

sealed abstract class ProfileProperty(val key: String, val defaultValue: String)

case object PPConsoleDivider extends ProfileProperty("console.divider","0.7")
case object PPHostUrl extends ProfileProperty("host.url","")
case object PPHostPort extends ProfileProperty("host.port","23")
case object PPScriptJail extends ProfileProperty("script.jail","")
case object PPScriptClasspath extends ProfileProperty("script.classpath","")
case object PPScriptClass extends ProfileProperty("script.class","")
case object PPAutoOpen extends ProfileProperty("profile.auto.open","false")
case object PPPromptPattern extends ProfileProperty("prompt.pattern",".*\\d*\\d*.*\\d.*")

object ProfileProperties {
  val properties = Set(PPConsoleDivider,PPHostUrl,PPHostPort,PPScriptJail,PPScriptClasspath,PPScriptClass,
    PPAutoOpen,PPPromptPattern)
}

trait CommandLineListener {
  def execute(command: String) : Unit
}

object Profile {

  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
  val defaultWindow = "default"

  val commandMap : Map[String,(Profile,String) => Unit] = {
    val cmap : Map[String,(Profile,String) => Unit]= Map(
      "open" -> ((p: Profile,s: String) => Profiles.open(s)),
      "list" -> ((p: Profile,s: String) => p.commandList),
      "reload" -> ((p: Profile,s: String) => p.startScript),
      "set" -> ((p: Profile,s: String) => p.commandSet(s)),
      "history" -> ((p: Profile,s: String) => p.commandHistory),
      "clear" -> ((p: Profile,s: String) => p.clear),
      "disconnect" -> ((p: Profile,s: String) => p.disconnect),
      "reconnect" -> ((p: Profile,s: String) => p.reconnect)
    )
    val amap : Map[String,(Profile,String) => Unit]= Map(
      "o" -> cmap("open"),
      "l" -> cmap("list"),
      "r" -> cmap("reload"),
      "s" -> cmap("set"),
      "hi" -> cmap("history"),
      "disco" -> cmap("disconnect"),
      "reco" -> cmap("reconnect")
    )

    cmap ++ amap
  }

}

class Profile(val name: String) extends AutoCloseable with CommandLineListener with ProfileEventListener {

  import Profile._

  val log = Profile.log

  val commandCharacter = "#"
  val properties = new Properties
  val propFile = new File(MainWindow.configDir, s"$name.profile")
  val textPanel = new SplitTextPanel
  val commandPane = new CommandPane(textPanel)
  val windows = mutable.Map[String,TextReceiver](defaultWindow -> textPanel)
  var telnet : Option[Telnet] = None
  val scriptManager = new ScriptManager

  Util.touch(propFile)
  load
  save
  commandPane.commandLine.addCommandLineListener(this)

  startScript
  info(s"profile: $name")
  log.info(s"opened profile $name")
  connect


  def commandHistory = {
    // TODO
  }

  def commandList = {
    info("\n---- settings -----------------------------------------------------\n")
    for((k,v) <- properties.asScala) info(String.format("     %-30s: %30s\n", k, v))
  }

  def commandSet(s: String): Unit = {
    val tokens: Array[String] = s.split(" ",2)
    if(tokens.length != 2) {
      info("ERROR: set command requires two arguments\n")
      return
    }

    val mp = ProfileProperties.properties.filter{ _.key == tokens(0) }

    if(mp.size == 0) {
      info(s"ERROR: no such property '${tokens(0)}'\n")
      return
    }

    set(mp.head,tokens(1))
    info(s"set property '${tokens(0)}' to '${tokens(1)}'\n")
  }

  def connect : Unit = synchronized {
    val url = getString(PPHostUrl)
    if(telnet.isEmpty && url.length > 0) {

      val port = Try {getInt(PPHostPort)} match {
        case Success(v) => v
        case Failure(e) =>
          info(s"failed to resolve port number for ${getString(PPHostPort)}")
          return
      }

      telnet = Some(new Telnet(url, port))
      telnet.get.addListener(this)
      info(s"connecting to ${telnet.get.address}")
      Util.invokeLater(() => telnet.get.connect)
    }
  }

  def disconnect = synchronized {
    telnet map {_.close}
    telnet = None
  }

  def reconnect = synchronized {
    disconnect
    connect
  }

  def clear = {
    // TODO
  }

  def info(s: String, window: String = defaultWindow) = windows(window).info(s)



  override def close(): Unit = ???

  def send(command: String): Unit = {
    telnet.map { t=>
      t.send(s"$command\r\n")
      textPanel.echo(s"$command\n", Some(Color.YELLOW))
    }
  }

  def handleCommand(command: String) : Unit = {
    log.debug("received command {}",command)
    val tokens = command.split(" ",2)
    val cmd = tokens(0).substring(1)

    if(!(commandMap contains cmd)) {
      info(s"ERROR: did not find command '$cmd'")
      return
    }

    val argString = if(tokens.length == 2)  tokens(1).trim else ""
    commandMap.get(cmd).map { _(this,argString)}
  }

  override def execute(command: String): Unit = {
    if(command.startsWith(commandCharacter)) {
      handleCommand(command)
      return
    }

    send(command)
  }

  def load = {
    for(p <- ProfileProperties.properties) properties.setProperty(p.key,p.defaultValue)

    TryWith(new FileInputStream(propFile)) {
      properties.load(_)
    } match {
      case Failure(e) => log.error(s"failed to load property file for $name",e)
      case _ =>
    }
  }


  def save = synchronized {
    TryWith(new FileOutputStream(propFile)) {
      properties.store(_,s"$name profile properties")
    } match {
      case Failure(e) => log.error("failed to save properties",e)
      case _ =>
    }
  }

  def raise : Unit = {
    commandPane.setVisible(true)
    commandPane.resize
  }

  def getInt(prop: ProfileProperty): Int = properties.getProperty(prop.key).toInt
  def getString(prop: ProfileProperty): String = properties.getProperty(prop.key)
  def getBoolean(prop: ProfileProperty): Boolean = properties.getProperty(prop.key).toBoolean

  def set(prop: ProfileProperty, v: Boolean) : Unit = set(prop,Option(v))
  def set(prop: ProfileProperty, v: String) : Unit = set(prop,Option(v))
  def set(prop: ProfileProperty, v: Int) : Unit = set(prop,Option(v))

  def set(prop: ProfileProperty, v: Option[Any]): Unit = {
    properties.setProperty(prop.key,v.map { _.toString } getOrElse { prop.defaultValue })
    save
  }

  def startScript = {
    Try {
      val classpath: String = getString(PPScriptClasspath)
      val script: String = getString(PPScriptClass)
      scriptManager.start(classpath, script, this)
    } match {
      case Failure(e) => log.error("failed to start script",e)
      case _ =>
    }
  }

  override def event(event: ProfileEvent, data: Option[String]): Unit = {
    event match {
      case TelnetDisconnect => info("disconnected")
      case TelnetError => data map {s=>info(s"ERROR: $s")}
      case TelnetConnect => info(s"connected to: ${telnet.get.address}")
      case TelnetRecv => data map { s=> windows(defaultWindow).addText(s)}
      case e => log.info(s"unsupported event $e with data $data")
    }
  }
}


object Profiles {
  import scala.collection._
  val log = Logger(LoggerFactory.getLogger(Profiles.getClass))

  val profiles : mutable.Map[String, Profile] = mutable.Map[String, Profile]()

  def apply(name: String) : Profile = synchronized { profiles(name) }

  def active = synchronized { Profiles(MainTabbedPane.active) }

  def open(name: String) : Profile = {
    val p : Profile = profiles.get(name).getOrElse {
      val p = new Profile(name)
      log.info("opening new profile {}",p.name)
      profiles += name -> p
      MainTabbedPane.addCommandPane(p.name,p.commandPane)
      //MainTabbedPane.setMnemonicAt(profiles.size - 1, KeyEvent.VK_0 + profiles.size)
      p
    }

    MainTabbedPane.setSelected(p.name)

    log.info("opened profile {}",p.name)
    p
  }

}