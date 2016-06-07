package aug.gui

import java.awt.{BorderLayout, Color, EventQueue}
import java.awt.event.KeyEvent
import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties
import javax.swing.{JPanel, JTabbedPane, UIManager}
import javax.swing.event.{ChangeEvent, ChangeListener}

import aug.util.{TryWith, Util}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.Failure

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

class Profile(val name: String) extends JPanel with AutoCloseable with CommandLineListener {

  import Profile._

  val log = Profile.log

  val commandLine = new CommandLine
  val commandCharacter = "#"
  val textPanel = new TextPanel
  val properties = new Properties
  val propFile = new File(MainWindow.configDir, s"$name.profile")

  setup

  def setup ={
    Util.touch(propFile)
    load
    save

    setLayout(null)
    textPanel.setVisible(true)
    add(textPanel)
    add(commandLine,BorderLayout.SOUTH)
    setBackground(Color.BLACK)

    setVisible(false)

    MainWindow.register(this)
    commandLine.addCommandLineListener(this)

    startScript
    connect
    echo(s"profile: $name")
    log.info(s"opened profile $name")
  }

  def commandHistory = {
    // TODO
  }

  def commandList = {
    echo("\n---- settings -----------------------------------------------------\n")
    for((k,v) <- properties.asScala) echo(String.format("     %-30s: %30s\n", k, v))
  }

  def commandSet(s: String): Unit = {
    val tokens: Array[String] = s.split(" ",2)
    if(tokens.length != 2) {
      echo("ERROR: set command requires two arguments\n")
      return
    }

    val mp = ProfileProperties.properties.filter{ _.key == tokens(0) }

    if(mp.size == 0) {
      echo(s"ERROR: no such property '${tokens(0)}'\n")
      return
    }

    set(mp.head,tokens(1))
    echo(s"set property '${tokens(0)}' to '${tokens(1)}'\n")
  }

  def connect = {

  }

  def disconnect = {
    // TODO
  }

  def reconnect = {
    // TODO
  }

  def clear = {
    // TODO
  }

  def echo(s: String) = textPanel.add(s)

  def resize : Unit = {
    val parent = getParent
    if(parent == null) return

    val remainingHeight = parent.getHeight - Profiles.getHeight

    val w = parent.getWidth
    setBounds(0, Profiles.getHeight, w, remainingHeight)

    val ch = 30
    commandLine.setBounds(0,getHeight - 30, getWidth,ch)

    val splitHeight = remainingHeight - ch
    textPanel.setBounds(0,0,getWidth,splitHeight)
    textPanel.resize
  }

  override def close(): Unit = ???

  def send(command: String): Unit = {

  }

  def handleCommand(command: String) : Unit = {
    log.debug("received command {}",command)
    val tokens = command.split(" ",2)
    val cmd = tokens(0).substring(1)

    if(!(commandMap contains cmd)) {
      echo(s"ERROR: did not find command '$cmd'")
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

  def raise(): Unit = {
    EventQueue.invokeLater(new Runnable() {
      override def run(): Unit = {
        MainWindow.toFront
        MainWindow.repaint()
        commandLine.requestFocusInWindow
      }
    })
  }

  def save = synchronized {
    TryWith(new FileOutputStream(propFile)) {
      properties.store(_,s"$name profile properties")
    } match {
      case Failure(e) => log.error("failed to save properties",e)
      case _ =>
    }
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
    // TODO
  }
}


object Profiles extends JTabbedPane with ChangeListener {
  import scala.collection._
  val log = Logger(LoggerFactory.getLogger(Profiles.getClass))

  val profiles : mutable.Map[String, Profile] = mutable.Map[String, Profile]()

  UIManager.put("TabbedPane.selected", Color.gray)
  setBackground(Color.GRAY)
  setForeground(Color.WHITE)
  setVisible(false)
  addChangeListener(this)

  def get(name: String) : Option[Profile] = synchronized {
    profiles.get(name)
  }

  def getActive(): Profile = synchronized { get(getTitleAt(getSelectedIndex)).get }

  def open(name: String) : Profile = {
    val p : Profile = get(name) getOrElse {
      val p = new Profile(name)
      profiles += name -> p
      addTab(p.name, null)
      setMnemonicAt(profiles.size - 1, KeyEvent.VK_0 + profiles.size)
      p
    }

    setVisible(true)

    setSelectedIndex(profiles.size - 1)
    MainWindow.add(p)

    p.commandLine.requestFocusInWindow

    p.setVisible(true)
    p.resize

    paintTabs

    log.info("opened new profile{}",p)
    p
  }

  def resize : Unit = synchronized {
    val p = getParent
    if(p==null) return
    val w = p.getWidth
    val h = 20
    setBounds(0,0,w,h)
    profiles.values.foreach { _.resize }
  }

  def paintTabs = synchronized {
    profiles.values.foreach { _.setVisible(false)}
    getActive.setVisible(true)
    getActive.commandLine.requestFocusInWindow
  }

  override def stateChanged(e: ChangeEvent): Unit = paintTabs
}