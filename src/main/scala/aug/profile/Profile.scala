package aug.profile

import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import aug.gui.{MainWindow, ProfilePanel}
import aug.io.Telnet
import aug.script.shared.ProfileInterface
import aug.script.{OutOfTimeException, Script, ScriptLoader}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

sealed trait ProfileEvent extends Comparable[ProfileEvent] {
  def priority : Int
  def subPriority : Long

  override def compareTo(other: ProfileEvent) : Int = {
    if (priority == other.priority) {
      val diff = subPriority - other.subPriority
      if (diff > 0) 1 else if (diff == 0) 0 else -1
    } else priority - other.priority
  }
}

abstract class AbstractProfileEvent(major: Int, minor: Long = EventId.nextId) extends ProfileEvent {
  override def priority: Int = major
  override def subPriority: Long = minor
}

private[profile] object EventId {
  private val next = new AtomicLong(Long.MinValue)
  def nextId = next.incrementAndGet()
}

case object TelnetConnect extends AbstractProfileEvent(Int.MaxValue - 1)
case class TelnetError(data: String) extends AbstractProfileEvent(0)
case object TelnetDisconnect extends AbstractProfileEvent(Int.MaxValue - 1)
case class TelnetRecv(data: String) extends AbstractProfileEvent(0)
case class TelnetGMCP(data: String) extends AbstractProfileEvent(0)

case object ProfileConnect extends AbstractProfileEvent(0)
case object ProfileDisconnect extends AbstractProfileEvent(0)
case object ClientStart extends AbstractProfileEvent(0)
case object ClientStop extends AbstractProfileEvent(0)

case class UserCommand(data: String) extends AbstractProfileEvent(Int.MaxValue - 1)

case object CloseProfile extends AbstractProfileEvent(Int.MaxValue)

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends ProfileInterface
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
  private val threadQueue = new PriorityBlockingQueue[ProfileEvent]()
  private val running = new AtomicBoolean(true)
  private val swallowNextCommand = new AtomicBoolean(false)
  thread.start()

  def setProfileConfig(profileConfig: ProfileConfig) = synchronized(this.profileConfig = profileConfig)

  def connect = offer(ProfileConnect)

  def reconnect = {
    offer(ProfileDisconnect)
    offer(ProfileConnect)
  }

  def disconnect = offer(ProfileDisconnect)

  private def threadLoop() : Unit = {
    while(running.get()) {
      Try {
        val event = threadQueue.take()
        event match {
          case TelnetConnect =>
            synchronized {
              profilePanel.addText("\n" + Util.colorCode("0") + "--connected--\n")
              slog.info(s"profile $name connected")
            }

            script.foreach(_.onConnect())

          case TelnetError(data) =>
            slog.info(s"profile $name: telnet error: $data")

          case TelnetDisconnect =>
            synchronized {
              profilePanel.addText("\n" + Util.colorCode("0") + "--disconnected--\n")
              slog.info(s"profile $name lost connection")
            }

            script.foreach(_.onDisconnect())

          case TelnetRecv(data) =>
            profilePanel.addText(data)

          case TelnetGMCP(data) =>

          case UserCommand(data) =>
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


          case CloseProfile =>
            telnet.foreach(_.close)
            mainWindow.tabbedPane.remove(profilePanel)

          case ProfileConnect =>
            telnet match {
              case Some(_) => slog.error("profile %s already connected", name)
              case None =>
                telnet = Some(new Telnet(this, profileConfig))
                slog.info(f"profile $name starting connection to ${profileConfig.telnetConfig.host}:" +
                  f"${profileConfig.telnetConfig.port}")
                telnet.foreach(_.connect)
            }

          case ProfileDisconnect =>
            telnet.foreach(_.close)
            telnet = None

          case ClientStart =>
            Try {
              script match {
                case Some(_) => throw new RuntimeException(s"profile $name failed to init client, already has a client")
                case None => ScriptLoader.constructScript(profileConfig)
              }
            } match {
              case Failure(e) =>
                slog.error(f"profile $name: failed to init script: ${e.getMessage}")
                log.error(f"profile $name: failed to init script", e)
              case Success(script) =>
                this.script = Some(script)
                Try {
                  script.init(this)
                } match {
                  case Failure(e) => slog.error(s"profile $name: failed to init client, won't autostart, ${e.getMessage}")
                  case Success(_) => slog.info(s"profile $name: started client successfully")
                }
            }

          case ClientStop =>
            script match {
              case Some(scr) =>
                script = None
                scr.shutdown()
              case None =>
                slog.error(f"profile $name: no client to shutdown")
            }

          case unhandledEvent =>
            log.error(s"unhandled event $unhandledEvent")
        }
      } match {
        case Failure(OutOfTimeException) =>
          log.error(s"profile $name: script ran out of time to respond")
          slog.error(s"profile $name: script ran out of time to respond")
          clientRestart
        case Failure(e) =>
          log.error("event handling failure", e)
          slog.error(f"event handling failure: ${e.getMessage}")
        case _ =>
      }
    }
  }

  def offer(event: ProfileEvent): Unit = {
    if (!threadQueue.offer(event)) {
      slog.error(f"profile $name failed to offer event $event")
      log.error(f"profile $name failed to offer event $event")
    }
  }

  def clientStart = offer(ClientStart)

  def clientStop = offer(ClientStop)

  def clientRestart = {
    offer(ClientStart)
    offer(ClientStop)
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      offer(CloseProfile)
      thread.join(1000)
    }
  }

  override def send(cmds: String): Unit = {
    telnet match {
      case Some(t) =>
        cmds.split("\n").foreach {cmd=>
          t.send(cmd + "\n")
          profilePanel.addText(cmd + "\n")
        }
      case None =>
        slog.info(s"profile $name: command ignored:g $cmds")
    }
  }
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
