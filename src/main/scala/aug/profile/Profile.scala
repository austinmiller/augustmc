package aug.profile

import java.util.concurrent.{PriorityBlockingQueue, TimeoutException}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import aug.gui.{MainWindow, ProfilePanel}
import aug.io.Telnet
import aug.script.shared.ProfileInterface
import aug.script.{Client, ScriptLoader}
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

sealed trait ProfileEvent extends Comparable[ProfileEvent] {
  def priority : Int
  def subPriority : Long

  override def compareTo(other: ProfileEvent) : Int = {
    val diff = if (priority == other.priority) {
      subPriority - other.subPriority
    } else {
      priority - other.priority
    }

    if (diff > 0) 1 else if (diff == 0) 0 else -1
  }
}

abstract class AbstractProfileEvent(major: Int, minor : Long) extends ProfileEvent {
  override def priority: Int = major
  override def subPriority: Long = minor
}

private[profile] object EventId {
  private val next = new AtomicLong(0)
  def nextId = next.incrementAndGet()
}

case class TelnetConnect(minor: Long = EventId.nextId) extends AbstractProfileEvent(Int.MinValue + 1, minor)
case class TelnetError(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetDisconnect(minor: Long = EventId.nextId) extends AbstractProfileEvent(Int.MinValue + 1, minor)
case class TelnetRecv(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetGMCP(data: String) extends AbstractProfileEvent(0, EventId.nextId)

case class ProfileConnect(minor: Long = EventId.nextId) extends AbstractProfileEvent(0, minor)
case class ProfileDisconnect(minor: Long = EventId.nextId) extends AbstractProfileEvent(0, minor)
case class ClientStart(minor: Long = EventId.nextId) extends AbstractProfileEvent(0, minor)
case class ClientStop(minor: Long = EventId.nextId) extends AbstractProfileEvent(0, minor)

case class UserCommand(data: String) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class SendData(data: String, silent: Boolean = false) extends
  AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)

case class CloseProfile(minor: Long = EventId.nextId) extends AbstractProfileEvent(Int.MinValue, minor)

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends ProfileInterface
  with AutoCloseable {
  import Profile.log
  import Util.Implicits._
  import mainWindow.slog

  val profilePanel = new ProfilePanel(mainWindow, this)
  val name = profileConfig.name
  mainWindow.tabbedPane.addProfile(name, profilePanel)

  private var telnet : Option[Telnet] = None
  private var client : Option[Client] = None
  private val thread = new Thread(threadLoop(), "ProfileThread: " + name)
  private val threadQueue = new PriorityBlockingQueue[ProfileEvent]()
  private val running = new AtomicBoolean(true)
  private var lineNum: Long = 0
  private var fragment: String = ""

  addLine("profile: " + profileConfig.name)

  thread.start()

  if (profileConfig.javaConfig.clientMode == "autostart") {
    offer(ClientStart())
  }

  def setProfileConfig(profileConfig: ProfileConfig) = synchronized(this.profileConfig = profileConfig)

  def connect = offer(ProfileConnect())

  def reconnect = {
    offer(ProfileDisconnect())
    offer(ProfileConnect())
  }

  def disconnect = offer(ProfileDisconnect())

  private def threadLoop() : Unit = {
    while(running.get()) {
      Try {
        val event = threadQueue.take()
        event match {
          case TelnetConnect(_) =>
            addLine(Util.colorCode("0") + "--connected--")
            slog.info(s"profile $name connected")

            client.foreach(_.onConnect())

          case TelnetError(data) =>
            slog.info(s"profile $name: telnet error: $data")

          case TelnetDisconnect(_) =>
            synchronized {
              addLine(Util.colorCode("0") + "--disconnected--")
              slog.info(s"profile $name: received disconnect command")
            }

            client.foreach(_.onDisconnect())

          case TelnetRecv(data) => processText(data)

          case TelnetGMCP(data) =>

          case UserCommand(data) =>
            client match {
              case Some(client) =>
                if(!client.handleCommand(data)) {
                  sendNow(data, false)
                }

              case None => sendNow(data, false)
            }

          case CloseProfile(_) =>
            Try { telnet.foreach(_.close) }
            Try { client.foreach(_.shutdown) }
            mainWindow.tabbedPane.remove(profilePanel)

          case ProfileConnect(_) =>
            telnet match {
              case Some(_) => slog.error("profile %s already connected", name)
              case None =>
                telnet = Some(new Telnet(this, profileConfig))
                slog.info(f"profile $name starting connection to ${profileConfig.telnetConfig.host}:" +
                  f"${profileConfig.telnetConfig.port}")
                telnet.foreach(_.connect)
            }

          case ProfileDisconnect(_) =>
            telnet.foreach(_.close)
            telnet = None

          case ClientStart(_) =>
            Try {
              client match {
                case Some(_) => throw new RuntimeException(s"profile $name failed to init client, already has a client")
                case None => ScriptLoader.constructScript(profileConfig)
              }
            } match {
              case Failure(e) =>
                slog.error(f"profile $name: failed to init script: ${e.getMessage}")
                log.error(f"profile $name: failed to init script", e)
              case Success(script) =>
                this.client = Some(script)
                Try {
                  script.init(this)
                } match {
                  case Failure(e) =>
                    slog.error(s"profile $name: failed to init client, won't autostart, ${e.getMessage}")
                    offer(ClientStop())
                  case Success(_) => slog.info(s"profile $name: started client successfully")
                }
            }

          case ClientStop(_) =>
            client match {
              case Some(scr) =>
                client = None
                scr.shutdown()
              case None =>
                slog.error(f"profile $name: no client to shutdown")
            }

          case SendData(cmds, silent) => sendNow(cmds, silent)

          case unhandledEvent =>
            log.error(s"unhandled event $unhandledEvent")
        }
      } match {
        case Failure(to: TimeoutException) => clientTimedOut()
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

  def clientStart = offer(ClientStart())

  def clientStop = offer(ClientStop())

  def clientRestart = {
    offer(ClientStart())
    offer(ClientStop())
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      offer(CloseProfile())
      thread.join(profileConfig.javaConfig.clientTimeout + 500)
    }
  }

  /**
    * <p>Echo cmd to console</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    */
  private def echoCommand(cmd: String) : Unit = {
    val ln = if (fragment.length > 0) lineNum else lineNum - 1
    profilePanel.addCommand(ln, cmd)
    profilePanel.repaint()
  }

  /**
    * <p>Add line to console</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    * @param line
    */
  private def addLine(line: String) : Unit = {
    profilePanel.setLine(lineNum, line)
    lineNum += 1
    fragment = ""
    profilePanel.repaint()
  }

  /**
    * <p>Handle client timing out.</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    */
  private def clientTimedOut() : Unit = {
    log.error(s"profile $name: script ran out of time to respond")
    slog.error(s"profile $name: script ran out of time to respond")
    offer(ClientStop())

    if (profileConfig.javaConfig.clientMode == "autostart") {
      offer(ClientStart())
    }
  }

  /**
    * <p>Process text sent from server.</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    * @param txt
    */
  private def processText(txt: String) : Unit = {

    @tailrec
    def handleText(texts: List[String], clientTimedOut: Boolean = false): Boolean = {
      texts match {

        case List(last) =>
          fragment += last
          profilePanel.setLine(lineNum, fragment)

          if (!clientTimedOut && client.isDefined) {
            Try {
              client.get.handleFragment(fragment)
            } match {
              case Failure(e: TimeoutException) => true
              case _ => clientTimedOut
            }
          } else clientTimedOut

        case text :: tail =>
          val line = fragment + text

          val didWeTimeout = if (!clientTimedOut && client.isDefined) {
            Try {
              if(!client.get.handleLine(lineNum, line)) {
                addLine(line)
              }
            } match {
              case Failure(e: TimeoutException) =>
                addLine(line)
                true
              case _ => false
            }
          } else {
            addLine(line)
            clientTimedOut
          }

          handleText(tail, didWeTimeout)

        case Nil => clientTimedOut
      }
    }

    if (handleText(txt.split("\n", -1).toList)) {
      clientTimedOut()
    }
  }

  /**
    * <p>Send text now, without using event loop.</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    * @param cmds
    */
  private def sendNow(cmds: String, silent: Boolean) : Unit = {
    telnet match {
      case Some(telnet) =>
        cmds.split("\n").foreach { cmd =>
          telnet.send(cmd + "\n")
          if (!silent) echoCommand(cmd)
        }

      case None => slog.info(s"profile $name: command ignored: $cmds")
    }

  }

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
   */
  override def send(cmds: String): Unit = offer(SendData(cmds))

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  override def sendSilently(cmds: String): Unit = offer(SendData(cmds, true))
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
