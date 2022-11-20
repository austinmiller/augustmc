package aug.gui.text

import aug.io.{ColorlessTextLogger, TextLogger}
import aug.profile.{Profile, ProfileConfig}
import aug.script.framework.{LineEvent, LineWithNum}
import aug.misc.Util.closeQuietly

import scala.annotation.tailrec

class ConsoleTextArea(profileConfig: ProfileConfig, profile: Profile) extends
  SplittableTextArea(profileConfig, profile) with AutoCloseable {

  import profile.withClient

  private val logDir = profile.logDir
  private val slog = profile.slog

  private var lastGA: Boolean = false
  private var lineNum: Long = 0
  private var nextLineNum: Long = 1
  private var fragment: String = ""
  private var textLogger : Option[TextLogger] = None
  private var colorlessTextLogger : Option[ColorlessTextLogger] = None

  def addLine(line: String, sendToClient: Boolean = false): Unit = synchronized {
    if (!sendToClient || !withClient(_.handleLine(new LineEvent(lineNum, line))).contains(true)) {
      text.setLine(lineNum, line)
    }

    lineNum = nextLineNum
    nextLineNum += 1
    fragment = ""
    repaint()
  }

  def appendFragment(line: String): Unit = synchronized {
    fragment += line
    withClient(_.handleFragment(new LineEvent(lineNum, fragment)))
    text.setLine(lineNum, fragment)
    repaint()
  }

  def processText(txt: String, ga: Boolean) : Unit = synchronized {

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

  def echoCommand(cmd: String) : Unit = synchronized {
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

  private def echoLine(line: String): Unit = {
    if (fragment == "") {
      addLine(line)
    } else {
      text.setLine(nextLineNum, line)
      nextLineNum += 1
    }
  }

  override def echo(line: String): Unit = synchronized {
    if (line.contains("\n")) {
      throw new RuntimeException("line may not contain newline")
    }

    echoLine(line)
    repaint()
  }

  override def echo(lines: Array[String]): Unit = synchronized {
    if (lines.exists(_.contains("\n"))) {
      throw new RuntimeException("line may not contain newline")
    }

    lines.foreach(echoLine)

    repaint()
  }

  override def clear(): Unit = synchronized {
    super.clear()
    lineNum = 0
    fragment = ""
    nextLineNum = 1
    lastGA = false
  }

  override def setLine(lineWithNum: LineWithNum): Unit = synchronized {
    if (lineWithNum.lineNum >= lineNum) {
      throw new RuntimeException(s"Cannot set lines on or after the active line (console window only) " +
        s"which is currently $lineNum")
    }

    super.setLine(lineWithNum)
  }

  override def setLines(lines: Array[LineWithNum]): Unit = synchronized {
    if (lines.exists(_.lineNum >= lineNum)) {
      throw new RuntimeException(s"Cannot set lines on or after the active line (console window only) " +
        s"which is currently $lineNum")
    }

    super.setLines(lines)
  }
}
