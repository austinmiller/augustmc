package aug.profile

import java.awt.{Color, Window}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

import aug.gui.{MainWindow, ProfilePanel}
import aug.io.{ColorUtils, Telnet}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Try}

trait ProfileEventListener {
  def event(event: ProfileEvent, data: Option[String])
}

trait ProfileInterface {
  val defaultWindow = "default"
  def send(s: String) : Unit
  def info(s: String, window: String = defaultWindow) : Unit
  def echo(s: String, color: Option[Color] = None, window: String = defaultWindow) : Unit
  def addColoredText(s: String, window: String = defaultWindow) : Unit
  def consumeNextCommand() : Unit
  def sendGmcp(s: String) : Unit
  def connected : Boolean
}

sealed trait ProfileEvent

case object TelnetConnect extends ProfileEvent
case object TelnetError extends ProfileEvent
case object TelnetDisconnect extends ProfileEvent
case object TelnetRecv extends ProfileEvent
case object TelnetGMCP extends ProfileEvent

case object ScriptClose extends ProfileEvent
case object ScriptInit extends ProfileEvent
case object ScriptNewLine extends ProfileEvent
case object ScriptFragment extends ProfileEvent
case object ScriptNewColorLine extends ProfileEvent
case object ScriptColorFragment extends ProfileEvent

case object UserCommand extends ProfileEvent

case class ProfileEventData(profileEvent: ProfileEvent, data: Option[String])

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends ProfileEventListener
  with AutoCloseable {
  import Profile.log
  import mainWindow.slog
  import Util.Implicits._

  val profilePanel = new ProfilePanel(mainWindow, this)
  val name = profileConfig.name
  mainWindow.tabbedPane.addProfile(name, profilePanel)

  profilePanel.addText("profile: " + profileConfig.name+"\n")

  @volatile
  private var telnet : Option[Telnet] = None
  private val thread = new Thread(threadLoop(), "ProfileThread: " + name)
  private val threadQueue = new LinkedBlockingQueue[ProfileEventData]()
  private val running = new AtomicBoolean(true)
  thread.start()

  def setProfileConfig(profileConfig: ProfileConfig) = synchronized(this.profileConfig = profileConfig)

  def connect = synchronized {
    telnet match {
      case Some(_) => slog.error("profile %s already connected", name)
      case None =>
        telnet = Some(new Telnet(profileConfig.telnetConfig.host, profileConfig.telnetConfig.port))
        telnet.foreach(_.addListener(this))
        Util.invokeLater(() => (telnet.foreach(_.connect)))
        slog.info(f"profile $name starting connection to ${profileConfig.telnetConfig.host}:" +
          f"${profileConfig.telnetConfig.port}")
    }
  }

  def reconnect = synchronized {
    disconnect
    connect
  }

  def disconnect = synchronized {
    telnet.foreach(_.close)
    telnet = None
  }

  private def threadLoop() : Unit = {
    while(running.get()) {
      Try {
        val ped = threadQueue.take()
        ped.profileEvent match {
          case TelnetConnect =>
            synchronized {
              profilePanel.addText("\n" + ColorUtils.colorCode("0") + "--connected--\n")
              slog.info(s"profile $name connected")
            }
          case TelnetError =>
          case TelnetDisconnect =>
            synchronized {
              profilePanel.addText("\n" + ColorUtils.colorCode("0") + "--disconnected--\n")
              slog.info(s"profile $name lost connection")
            }
          case TelnetRecv =>
            ped.data.foreach(profilePanel.addText)
          case TelnetGMCP =>
          case ScriptClose =>
          case ScriptInit =>
          case ScriptNewLine =>
          case ScriptFragment =>
          case ScriptNewColorLine =>
          case ScriptColorFragment =>
          case UserCommand =>
            ped.data.foreach { data =>
              telnet match {
                case Some(t) =>
                  t.send(data + "\n")
                  profilePanel.addText(data + "\n")
                case None =>
                  slog.info(s"profile $name: command ignored: $data")
              }
            }
        }
      } match {
        case Failure(e) =>
          log.error("event handling failure", e)
          slog.error(f"event handling failure: ${e.getMessage}")
        case _ =>
      }
    }
  }

  override def event(event: ProfileEvent, data: Option[String]): Unit = {
    val ped = new ProfileEventData(event, data)
    if(!threadQueue.offer(ped)) {
      slog.error(f"profile $name failed to offer event $ped")
      log.error(f"profile $name failed to offer event $ped")
    }
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      thread.join(1000)
    }

    disconnect

    mainWindow.tabbedPane.remove(profilePanel)
  }
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
