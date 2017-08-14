package aug.profile

import java.awt.Component
import java.io.File
import java.lang.Boolean
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{PriorityBlockingQueue, TimeoutException}
import javax.swing.{BorderFactory, JSplitPane, SwingUtilities}

import aug.gui.{HasHighlight, MainWindow, ProfilePanel, SplittableTextArea}
import aug.io.{ColorlessTextLogger, Mongo, PrefixSystemLog, Telnet, TextLogger}
import aug.script.framework._
import aug.script.framework.tools.ScalaUtils
import aug.script.{Client, ClientCaller, ScriptLoader}
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
  def nextId: Long = next.incrementAndGet()
}

case class CloseProfile() extends AbstractProfileEvent(Int.MinValue, EventId.nextId)

case class TelnetConnect(id: Long, url: String, port: Int) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class TelnetDisconnect(id: Long) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class UserCommand(data: String) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class SendData(data: String, silent: Boolean = false) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class ProfileLog(on: Boolean, color: Boolean) extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class MongoStart() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)
case class MongoStop() extends AbstractProfileEvent(Int.MinValue + 1, EventId.nextId)

case class ClientEvent(event: ClientCaller) extends AbstractProfileEvent(Int.MinValue + 2, EventId.nextId)

case class TelnetError(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetRecv(data: String) extends AbstractProfileEvent(0, EventId.nextId)
case class TelnetGMCP(data: String) extends AbstractProfileEvent(0, EventId.nextId)

case class ProfileConnect() extends AbstractProfileEvent(0, EventId.nextId)
case class ProfileDisconnect() extends AbstractProfileEvent(0, EventId.nextId)
case class ClientStart() extends AbstractProfileEvent(0, EventId.nextId)
case class ClientStop() extends AbstractProfileEvent(0, EventId.nextId)

class Profile(private var profileConfig: ProfileConfig, mainWindow: MainWindow) extends AutoCloseable
  with HasHighlight {

  import Profile.log
  import Util.Implicits._

  val profilePanel = new ProfilePanel(mainWindow, this)
  val name: String = profileConfig.name
  val slog = new PrefixSystemLog(s"[$name]: ", mainWindow.slog)
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
  private var schedulerState = List.empty[String]

  val console = new SplittableTextArea(profileConfig, this)
  windows("console") = console

  addLine("profile: " + profileConfig.name)

  thread.setUncaughtExceptionHandler((t: Thread, e: Throwable) => {
    e.printStackTrace()
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

  if (profileConfig.autoLog == "without color" || profileConfig.autoLog == "both") {
    offer(ProfileLog(true, false))
  }

  if (profileConfig.autoLog == "with color" || profileConfig.autoLog == "both") {
    offer(ProfileLog(true, true))
  }

  def setProfileConfig(profileConfig: ProfileConfig): Unit = synchronized {
    this.profileConfig = profileConfig
    profilePanel.setProfileConfig(profileConfig)
    windows.values.foreach({ w =>
      w.setProfileConfig(profileConfig)
      w.setActiveFont(profileConfig.consoleWindow.font.toFont)
      w.repaint()
    })
  }

  def connect(): Unit = offer(ProfileConnect())

  def reconnect(): Unit = {
    offer(ProfileDisconnect())
    offer(ProfileConnect())
  }

  def disconnect(): Unit = offer(ProfileDisconnect())

  private def threadLoop() : Unit = {
    while(running.get()) {
      try {
        val event = threadQueue.take()
        event match {
          case TelnetConnect(id, url, port) =>
            addLine(ScalaUtils.encodeColor("0") + "--connected--")
            slog.info(s"connected $telnet")

            client.foreach(_.onConnect(id, url, port))

          case TelnetError(data) =>
            slog.info(s"telnet error: $data")

          case TelnetDisconnect(id) =>
            onDisconnect(id)
            slog.info(s"disconnected $id")

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
            closeQuietly(telnet.foreach(_.close()))
            closeQuietly(client.foreach(_.shutdown()))
            closeQuietly(mongo.foreach(_.close()))
            closeQuietly(textLogger.foreach(_.close()))
            closeQuietly(colorlessTextLogger.foreach(_.close()))
            mainWindow.tabbedPane.remove(profilePanel)

          case ProfileConnect() =>
            telnet match {
              case Some(_) => slog.error(s"already connected")
              case None =>
                telnet = Some(new Telnet(this, profileConfig))
                slog.info(f"starting connection to ${profileConfig.telnetConfig.host}:" +
                  f"${profileConfig.telnetConfig.port}")
                telnet.foreach(_.connect())
            }

          case ProfileDisconnect() =>
            telnet.foreach {t =>
              onDisconnect(t.id)
              t.close()
            }

          case ClientEvent(clientCaller) =>
            clientCaller.callOnClient()

          case ClientStart() =>
            Try {
              client match {
                case Some(_) => throw new RuntimeException(s"failed to init client, already has a client")
                case None => ScriptLoader.constructScript(this, profileConfig)
              }
            } match {
              case Failure(e) =>
                slog.error(f"failed to init script: ${e.getMessage}")
                log.error(f"failed to init script", e)
              case Success(script) =>
                this.client = Some(script)
                Try {
                  script.init(new ProfileProxy(this), clientReloadData)
                } match {
                  case Failure(e) =>
                    slog.error(s"failed to init client, won't autostart, ${e.getMessage}")
                    offer(ClientStop())
                  case Success(_) => slog.info(s"started client successfully")
                }
            }

          case ClientStop() =>
            client match {
              case Some(scr) =>
                client = None
                schedulerState = scr.schedulerState
                clientReloadData = scr.shutdown()
              case None =>
                slog.info(f"no client to shutdown")
            }

          case SendData(cmds, silent) => sendNow(cmds, silent)

          case ProfileLog(on, color) =>
            if (!on && color && textLogger.isDefined) {
              closeQuietly(textLogger.foreach(_.close()))
              textLogger = None
              slog.info(s"no longer logging colored text")
            } else if (on && color && textLogger.isEmpty) {
              textLogger = Some(new TextLogger(logDir))
              slog.info(s"logging color to $logDir")
            } else if (!on && !color && colorlessTextLogger.isDefined) {
              closeQuietly(colorlessTextLogger.foreach(_.close()))
              colorlessTextLogger = None
              slog.info(s"no longer logging")
            } else if (on && !color && textLogger.isEmpty) {
              colorlessTextLogger = Some(new ColorlessTextLogger(logDir))
              slog.info(s"logging to $logDir")
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
          slog.error(s"event handling failure: ${e.getMessage}")
      }
    }
    slog.info(s"event thread exiting")
  }

  def unsplitAll(): Unit = windows.values.foreach(_.unsplit())

  def offer(event: ProfileEvent): Unit = {
    if (!threadQueue.offer(event)) {
      slog.error(f"failed to offer event $event")
      log.error(f"failed to offer event $event")
    }
  }

  def mongoStart(): Unit = offer(MongoStart())

  def mongoStop(): Unit = offer(MongoStop())

  def mongoRestart(): Unit = {
    offer(MongoStop())
    offer(MongoStart())
  }

  def clientStart(): Unit = offer(ClientStart())

  def clientStop(): Unit = offer(ClientStop())

  def clientRestart(): Unit = {
    offer(ClientStop())
    offer(ClientStart())
  }

  override def close(): Unit = {
    if(running.compareAndSet(true, false)) {
      slog.info(s"closing profile")
      offer(CloseProfile())
      thread.join(profileConfig.javaConfig.clientTimeout + 500)
    }
  }

  def handleClientException(throwable: Throwable): Unit = {
    slog.error(s"received exception from client", throwable)
  }

  private def closeQuietly[T](f: => T): Unit = {
    try {
      f
    } catch {
      case _: Throwable =>
    }
  }

  /**
    * <p>Handle disconnect whether by server or client.</p>
    *
    * <p><STRONG>This should only be called by the event thread!</STRONG></p>
    *
    */
  private def onDisconnect(id: Long): Unit = {
    telnet.foreach{ t=>
      if (t.id == id) {
        addLine(ScalaUtils.encodeColor("0") + "--disconnected--")
        client.foreach(_.onDisconnect(id))
      }
      telnet = None
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
    log.error(s"script ran out of time to respond")
    slog.error(s"script ran out of time to respond")
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
              client.get.handleFragment(new LineEvent(lineNum, fragment))
            } match {
              case Failure(e: TimeoutException) => true
              case _ => clientTimedOut
            }
          } else clientTimedOut

        case text :: tail =>
          val line = fragment + text

          val didWeTimeout: Boolean = if (!clientTimedOut && client.isDefined) {
            Try {
              if(!client.get.handleLine(new LineEvent(lineNum, line))) {
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

      case None => slog.info(s"command ignored: $cmds")
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
      slog.error(s"not every name in $names existed in ${windows.keys}")
      return false
    }

    if (!names.contains("console")) {
      slog.error(s"window graph did not contain windows console")
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
    Util.invokeLater(10, () => SwingUtilities.invokeLater(() => {
      dividerLocations.foreach(s => s._1.setDividerLocation(s._2))
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
      val sta = new SplittableTextArea(profileConfig, this, false)
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

  /**
    * <p><STRONG>This should *only* be called by the client.</STRONG></p>
    */
  def getScheduler(reloaders: Seq[RunnableReloader[_ <: Runnable]]): SchedulerInterface = {
    client.map(_.getScheduler(schedulerState, reloaders)).getOrElse(throw new RuntimeException("client not found"))
  }
}

object Profile {
  val log = Logger(LoggerFactory.getLogger(Profile.getClass))
}
