package aug.gui.text

import aug.io.{ColorlessTextLogger, TextLogger}
import aug.profile.{Profile, ProfileConfig}
import aug.script.framework.LineEvent
import aug.util.Util.closeQuietly

import scala.annotation.tailrec

class ConsoleTextArea(profileConfig: ProfileConfig, profile: Profile) extends
  SplittableTextArea(profileConfig, profile) with AutoCloseable {

  import profile.withClient

  private val logDir = profile.logDir
  private val slog = profile.slog
  private var lastGA: Boolean = false
  private var lineNum: Long = 0
  private var fragment: String = ""
  private var textLogger : Option[TextLogger] = None
  private var colorlessTextLogger : Option[ColorlessTextLogger] = None

  def addLine(line: String, sendToClient: Boolean = false): Unit = {
    if (!sendToClient || !withClient(_.handleLine(new LineEvent(lineNum, line))).contains(true)) {
      text.setLine(lineNum, line)
    }

    lineNum += 1
    fragment = ""
    repaint()
  }

  def appendFragment(line: String): Unit = {
    fragment += line
    withClient(_.handleFragment(new LineEvent(lineNum, fragment)))
    text.setLine(lineNum, fragment)
    repaint()
  }

  def processText(txt: String, ga: Boolean) : Unit = {

    if (lastGA) {
      addLine(fragment)
    }

    lastGA = ga

    textLogger.foreach(_.addText(txt))
    colorlessTextLogger.foreach(_.addText(txt))

    @tailrec
    def handleText(texts: List[String]): Unit = {
      texts match {
        case List(last) =>
          appendFragment(last)

        case xs :: tail =>
          addLine(fragment + xs, sendToClient = true)
          handleText(tail)

        case Nil =>
      }
    }

    // the -1 is necessary not to swallow empty splits
    handleText(txt.split("\n", -1).toList)
  }

  def echoCommand(cmd: String) : Unit = {
    val ln = if (fragment.length > 0) lineNum else lineNum - 1
    text.addCommand(ln, cmd)
    repaint()
  }

  def log(on: Boolean, color: Boolean): Unit = {
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
  }

  override def close(): Unit = {
    closeQuietly(textLogger.foreach(_.close()))
    closeQuietly(colorlessTextLogger.foreach(_.close()))
  }
}
