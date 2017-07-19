package aug.profile

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import aug.gui.{MainWindow, ProfilePanel}
import aug.io.{ColorUtils, Telnet}
import aug.script.{OutOfTimeException, Script, ScriptLoader}
import aug.script.shared.ProfileInterface
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

trait ProfileEventListener {
  def event(event: ProfileEventType, data: Option[String])
}

sealed trait ProfileEventType

case object TelnetConnect extends ProfileEventType
case object TelnetError extends ProfileEventType
case object TelnetDisconnect extends ProfileEventType
case object TelnetRecv extends ProfileEventType
case object TelnetGMCP extends ProfileEventType

case object ScriptInit extends ProfileEventType

case object UserCommand extends ProfileEventType

case object CloseProfile extends ProfileEventType

case class ProfileEvent(profileEvent: ProfileEventType, data: Option[String] = None)

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends ProfileEventListener
  with ProfileInterface
  with AutoCloseable {
  import Profile.log
  import Util.Implicits._
  import mainWindow.slog

  val profilePanel = new ProfilePanel(mainWindow, this)
  val name = profileConfig.name
  mainWindow.tabbedPane.addProfile(name, profilePanel)

  profilePanel.addText("profile: " + profileConfig.name+"\n")

  @volatile
  private var telnet : Option[Telnet] = None
  private var script : Option[Script] = None
  private val thread = new Thread(threadLoop(), "ProfileThread: " + name)
  private val threadQueue = new LinkedBlockingQueue[ProfileEvent]()
  private val running = new AtomicBoolean(true)
  private val swallowNextCommand = new AtomicBoolean(false)
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

            script.foreach(_.onConnect())

          case TelnetError =>
            slog.info(s"profile $name: telnet error")

          case TelnetDisconnect =>
            synchronized {
              profilePanel.addText("\n" + ColorUtils.colorCode("0") + "--disconnected--\n")
              slog.info(s"profile $name lost connection")
            }

            script.foreach(_.onDisconnect())

          case TelnetRecv =>
            ped.data.foreach(profilePanel.addText)

          case TelnetGMCP =>

          case ScriptInit =>
            script.foreach(_.init(this))

          case UserCommand =>
            ped.data.foreach { data =>

              val exception = Try {
                script.foreach(_.handleCommand(data))
              } match {
                case Failure(e) => Some(e)
                case _ => None
              }

              if (!swallowNextCommand.compareAndSet(true, false)) {
                send(data)
              }

              exception.foreach(e => throw e)
            }

          case CloseProfile =>
        }
      } match {
        case Failure(OutOfTimeException) =>
          log.error(s"profile $name: script ran out of time to respond")
          slog.error(s"profile $name: script ran out of time to respond")
          scriptRestart
        case Failure(e) =>
          log.error("event handling failure", e)
          slog.error(f"event handling failure: ${e.getMessage}")
        case _ =>
      }
    }
  }

  override def event(event: ProfileEventType, data: Option[String]): Unit = {
    val ped = new ProfileEvent(event, data)
    offer(ped)
  }


  private def offer(pe: ProfileEventType): Unit = offer(ProfileEvent(pe))

  private def offer(ped: ProfileEvent): Unit = {
    if (!threadQueue.offer(ped)) {
      slog.error(f"profile $name failed to offer event $ped")
      log.error(f"profile $name failed to offer event $ped")
    }
  }

  def scriptInit = synchronized {
    Try {
      script match {
        case Some(_) => throw new RuntimeException(s"profile $name failed to init script, already has a script")
        case None => ScriptLoader.constructScript(profileConfig)
      }
    } match {
      case Failure(e) =>
        slog.error(f"profile $name failed to init script: ${e.getMessage}")
        log.error(f"profile $name failed to init script", e)
      case Success(script) =>
        this.script = Some(script)
        offer(ProfileEvent(ScriptInit))
    }
  }

  def scriptShutdown = synchronized {
    script match {
      case Some(scr) =>
        script = None
        scr.shutdown()
      case None =>
        slog.error(f"profile $name no script to shutdown")
        log.error(f"profile $name no script to shutdown")
    }
  }

  def scriptRestart = synchronized {
    scriptShutdown
    scriptInit
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      offer(CloseProfile)
      thread.join(1000)
    }

    disconnect

    mainWindow.tabbedPane.remove(profilePanel)
  }

  override def send(cmds: String): Unit = {
    telnet match {
      case Some(t) =>
        cmds.split("\n").foreach {cmd=>
          t.send(cmd + "\n")
          profilePanel.addText(cmd + "\n")
        }
      case None =>
        slog.info(s"profile $name: command ignored: $cmds")
    }
  }
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
