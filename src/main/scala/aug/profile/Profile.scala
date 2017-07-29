package aug.profile

import java.awt.Component
import java.io.File
import java.lang.Boolean
import java.lang.Thread.UncaughtExceptionHandler
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{PriorityBlockingQueue, TimeoutException}
import javax.swing.{BorderFactory, JSplitPane, SwingUtilities}

import aug.gui.{MainWindow, ProfilePanel, SplittableTextArea}
import aug.io.{ColorlessTextLogger, Mongo, Telnet, TextLogger}
import aug.script.shared._
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

case class CloseProfile() extends AbstractProfileEvent(Int.MinValue, EventId.nextId)

case class TelnetConnect() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class TelnetDisconnect() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class UserCommand(data: String) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class SendData(data: String, silent: Boolean = false) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class ProfileLog(on: Boolean, color: Boolean) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class MongoStart() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class MongoStop() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)

case class TelnetError(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetRecv(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetGMCP(data: String) extends AbstractProfileEvent(0, EventId.nextId)

case class ProfileConnect() extends AbstractProfileEvent(0, EventId.nextId)
case class ProfileDisconnect() extends AbstractProfileEvent(0, EventId.nextId)
case class ClientStart() extends AbstractProfileEvent(0, EventId.nextId)
case class ClientStop() extends AbstractProfileEvent(0, EventId.nextId)

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends AutoCloseable {
  import Profile.log
  import Util.Implicits._

  val slog = mainWindow.slog

  val profilePanel = new ProfilePanel(mainWindow, this)
  val name = profileConfig.name
  mainWindow.tabbedPane.addProfile(name, profilePanel)

  private val thread = new Thread(threadLoop(), "ProfileThread: " + name)
  private val windows = scala.collection.mutable.Map[String, SplittableTextArea]()
  private val threadQueue = new PriorityBlockingQueue[ProfileEvent]()
  private val running = new AtomicBoolean(true)
  private val logDir = new File(ConfigManager.getProfileDir(name), "log")
  logDir.mkdir()

  private var telnet : Option[Telnet] = None
  private var client : Option[Client] = None
  private var mongo : Option[Mongo] = None
  private var textLogger : Option[TextLogger] = None
  private var colorlessTextLogger : Option[ColorlessTextLogger] = None
  private var lineNum: Long = 0
  private var fragment: String = ""
  private var clientReloadData = new ReloadData

  val console = new SplittableTextArea()
  windows("console") = console

  addLine("profile: " + profileConfig.name)

  thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
    override def uncaughtException(t: Thread, e: Throwable): Unit = {
      e.printStackTrace()
    }
  })

  thread.start()

  profilePanel.setProfileConfig(profileConfig)
  profilePanel.setContents(console)
  setProfileConfig(profileConfig)

  if (profileConfig.javaConfig.clientMode == "autostart") {
    offer(ClientStart())
  }

  if (profileConfig.mongoConfig.enabled) {
    offer(MongoStart())
  }

  offer(ProfileLog(true, false))
  offer(ProfileLog(true, true))

  def setProfileConfig(profileConfig: ProfileConfig) = synchronized {
    this.profileConfig = profileConfig
    profilePanel.setProfileConfig(profileConfig)
    windows.values.foreach({ w =>
      w.setActiveFont(profileConfig.consoleWindow.font.toFont)
      w.repaint()
    })
  }

  def connect() = offer(ProfileConnect())

  def reconnect() = {
    offer(ProfileDisconnect())
    offer(ProfileConnect())
  }

  def disconnect() = offer(ProfileDisconnect())

  private def threadLoop() : Unit = {
    while(running.get()) {
      try {
        val event = threadQueue.take()
        event match {
          case TelnetConnect() =>
            addLine(Util.colorCode("0") + "--connected--")
            slog.info(s"profile $name connected")

            client.foreach(_.onConnect())

          case TelnetError(data) =>
            slog.info(s"profile $name: telnet error: $data")

          case TelnetDisconnect() =>
            synchronized {
              addLine(Util.colorCode("0") + "--disconnected--")
              slog.info(s"profile $name: received disconnect command")
            }

            client.foreach(_.onDisconnect())

          case TelnetRecv(data) => processText(data)

          case TelnetGMCP(data) =>

          case UserCommand(data) =>
            client match {
              case Some(c) =>
                if(!c.handleCommand(data)) {
                  sendNow(data, false)
                }

              case None => sendNow(data, false)
            }

          case CloseProfile() =>
            closeQuietly(telnet.foreach(_.close))
            closeQuietly(client.foreach(_.shutdown()))
            closeQuietly(mongo.foreach(_.close()))
            closeQuietly(textLogger.foreach(_.close()))
            closeQuietly(colorlessTextLogger.foreach(_.close()))
            mainWindow.tabbedPane.remove(profilePanel)

          case ProfileConnect() =>
            telnet match {
              case Some(_) => slog.error(s"profile $name: already connected")
              case None =>
                telnet = Some(new Telnet(this, profileConfig))
                slog.info(f"profile $name starting connection to ${profileConfig.telnetConfig.host}:" +
                  f"${profileConfig.telnetConfig.port}")
                telnet.foreach(_.connect)
            }

          case ProfileDisconnect() =>
            telnet.foreach(_.close)
            telnet = None

          case ClientStart() =>
            Try {
              client match {
                case Some(_) => throw new RuntimeException(s"profile $name failed to init client, already has a client")
                case None => ScriptLoader.constructScript(this, profileConfig)
              }
            } match {
              case Failure(e) =>
                slog.error(f"profile $name: failed to init script: ${e.getMessage}")
                log.error(f"profile $name: failed to init script", e)
              case Success(script) =>
                this.client = Some(script)
                Try {
                  script.init(new ProfileProxy(this), clientReloadData)
                } match {
                  case Failure(e) =>
                    slog.error(s"profile $name: failed to init client, won't autostart, ${e.getMessage}")
                    offer(ClientStop())
                  case Success(_) => slog.info(s"profile $name: started client successfully")
                }
            }

          case ClientStop() =>
            client match {
              case Some(scr) =>
                client = None
                clientReloadData = scr.shutdown()
              case None =>
                slog.error(f"profile $name: no client to shutdown")
            }

          case SendData(cmds, silent) => sendNow(cmds, silent)

          case ProfileLog(on, color) =>
            if (!on && color && textLogger.isDefined) {
              closeQuietly(textLogger.foreach(_.close()))
              textLogger = None
              slog.info(s"profile $name: no longer logging colored text")
            } else if (on && color && textLogger.isEmpty) {
              textLogger = Some(new TextLogger(logDir))
              slog.info(s"profile $name: logging color to $logDir")
            } else if (!on && !color && colorlessTextLogger.isDefined) {
              closeQuietly(colorlessTextLogger.foreach(_.close()))
              colorlessTextLogger = None
              slog.info(s"profile $name: no longer logging")
            } else if (on && !color && textLogger.isEmpty) {
              colorlessTextLogger = Some(new ColorlessTextLogger(logDir))
              slog.info(s"profile $name: logging to $logDir")
            }

          case MongoStart() =>
            mongo = Some(new Mongo(this, profileConfig))

          case MongoStop() =>
            closeQuietly(mongo.foreach(_.close()))
            mongo = None

          case unhandledEvent =>
            log.error(s"unhandled event $unhandledEvent")
        }
      } catch {
        case to: TimeoutException => clientTimedOut()
        case e: Throwable =>
          log.error("event handling failure", e)
          slog.error(s"profile $name: event handling failure: ${e.getMessage}")
      }
    }
    slog.info(s"profile $name: event thread exiting")
  }

  def offer(event: ProfileEvent): Unit = {
    if (!threadQueue.offer(event)) {
      slog.error(f"profile $name: failed to offer event $event")
      log.error(f"profile $name failed to offer event $event")
    }
  }


  def mongoStart() = offer(MongoStart())

  def mongoStop() = offer(MongoStop())

  def mongoRestart() = {
    offer(MongoStop())
    offer(MongoStart())
  }

  def clientStart() = offer(ClientStart())

  def clientStop() = offer(ClientStop())

  def clientRestart() = {
    offer(ClientStop())
    offer(ClientStart())
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      slog.info(s"profile $name: closing profile")
      offer(CloseProfile())
      thread.join(profileConfig.javaConfig.clientTimeout + 500)
    }
  }

  def handleClientException(throwable: Throwable): Unit = {
    slog.error(s"profile $name: received exception from client", throwable)
  }

  private def closeQuietly[T](f: => T): Unit = {
    try {
      f
    } catch {
      case _: Throwable =>
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
    console.text.addCommand(ln, cmd)
    console.repaint()
  }

  /**
    * <p>Add line to console</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    */
  private def addLine(line: String) : Unit = {
    console.text.setLine(lineNum, line)
    lineNum += 1
    fragment = ""
    console.repaint()
  }

  /**
    * <p>Handle client timing out.</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
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
    */
  private def processText(txt: String) : Unit = {

    textLogger.foreach(_.addText(txt))
    colorlessTextLogger.foreach(_.addText(txt))

    @tailrec
    def handleText(texts: List[String], clientTimedOut: Boolean = false): Boolean = {
      texts match {

        case List(last) =>
          fragment += last
          console.text.setLine(lineNum, fragment)

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

          val didWeTimeout: Boolean = if (!clientTimedOut && client.isDefined) {
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
    */
  private def sendNow(cmds: String, silent: Boolean) : Unit = {
    telnet match {
      case Some(t) =>
        cmds.split("\n").foreach { cmd =>
          t.send(cmd + "\n")
          if (!silent) echoCommand(cmd)
        }

      case None => slog.info(s"profile $name: command ignored: $cmds")
    }

  }

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  private[profile] def setWindowGraph(windowReference: WindowReference): java.lang.Boolean = {

    @tailrec
    def getNames(windows: List[WindowReference], names: List[String] = List.empty): List[String] = {
      if (windows.isEmpty) {
        names
      } else {
        val newNames = windows.map(_.getName).filter(!_.isEmpty)
        val newWindows = windows.filter(_.isInstanceOf[SplitWindow])
          .map(_.asInstanceOf[SplitWindow])
          .flatMap(sw => List(sw.getTopLeft, sw.getBotRight))
        getNames(newWindows, names ++ newNames)
      }
    }

    val names = getNames(List(windowReference))

    if (names.exists(windows.get(_).isEmpty)) {
      slog.error(s"profile $name: not every name in $names existed in ${windows.keys}")
      return false
    }

    if (!names.contains("console")) {
      slog.error(s"profile $name: window graph did not contain windows console")
      return false
    }

    def convertToComponents(windowReference: WindowReference): (Component, List[(JSplitPane, Float)]) = {
      windowReference match {
        case sw: SplitWindow =>

          val (c1, l1) = convertToComponents(sw.getTopLeft)
          val (c2, l2) = convertToComponents(sw.getBotRight)

          val splitPanel = new JSplitPane()
          splitPanel.setDividerSize(2)
          splitPanel.setBorder(BorderFactory.createEmptyBorder())

          if (sw.isHorizontal) {
            splitPanel.setOrientation(JSplitPane.HORIZONTAL_SPLIT)
            splitPanel.setLeftComponent(c1)
            splitPanel.setRightComponent(c2)
          } else {
            splitPanel.setOrientation(JSplitPane.VERTICAL_SPLIT)
            splitPanel.setTopComponent(c1)
            splitPanel.setRightComponent(c2)
          }

          (splitPanel, l1 ++ l2 :+ (splitPanel, sw.getDividerLocation))
        case _ => (windows(windowReference.getName), List.empty)
      }
    }

    val (component, dividerLocations) = convertToComponents(windowReference)

    profilePanel.setContents(component)

    // really terrible hack
    Util.invokeLater(10, () => SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        dividerLocations.foreach(s => s._1.setDividerLocation(s._2))
      }
    }))

    true
  }

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  private[profile] def getWindowNames: util.List[String] = {
    import scala.collection.JavaConverters._
    windows.keys.toList.asJava
  }

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  private[profile] def createTextWindow(name: String): TextWindowInterface = {
    windows.getOrElseUpdate(name, {
      val sta = new SplittableTextArea(true)
      sta.setActiveFont(profileConfig.consoleWindow.font.toFont)
      sta
    })
  }

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  private[profile] def getTextWindow(name: String): TextWindowInterface = {
    windows.getOrElse(name, throw new RuntimeException(s"no window found with name $name"))
  }
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
