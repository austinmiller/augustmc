package aug.gui

import java.awt.event.{MouseWheelEvent, MouseWheelListener}
import java.awt.image.BufferedImage
import java.awt.{Font, Graphics}
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JSplitPane}

import aug.io._
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

case class Fragment(text: String, colorCode: ColorCode) {
  def splitAt(index: Int) : (Fragment, Fragment) = {
    (Fragment(text.substring(0, index), colorCode), Fragment(text.substring(index), colorCode))
  }
}

case class Line(fragments: List[Fragment], commands: List[String]) {
  def :+(fragment: Fragment) = {
    if (!fragments.isEmpty && fragments.last.colorCode == fragment.colorCode) {
      appendToLastFragment(fragment.text)
    } else new Line(fragments :+ fragment, commands)
  }

  private def appendToLastFragment(text: String) = {
    val last = fragments.last
    new Line(fragments.dropRight(1) :+ Fragment(last.text + text, last.colorCode), commands)
  }

  def mergeCommands : Line = {
    if (commands.isEmpty) this else {
      val frag = Fragment(commands.mkString(" | "), ColorCode(TelnetColorYellow))
      this.copy(fragments = this.fragments :+ frag)
    }
  }

  def split(wrapAt: Int) : List[Line] = {

    if (fragments.isEmpty) {
      return List[Line](this)
    }

    val lines = List.newBuilder[Line]
    val frags = List.newBuilder[Fragment]

    def addLine(fragment: Fragment) = {
      frags += fragment
      lines += new Line(frags.result, List.empty)
      frags.clear
    }

    @tailrec
    def addFragments(fragments: List[Fragment], fragment: Fragment, room: Int = wrapAt) : Unit = {
      if (fragment.text.length == room) {
        addLine(fragment)

        if (fragments != Nil) {
          addFragments(fragments.tail, fragments.head)
        }
      } else if (fragment.text.length < room) {
        frags += fragment

        if (fragments != Nil) {
          addFragments(fragments.tail, fragments.head, room - fragment.text.length)
        }
      } else {
        val (head, tail) = fragment.splitAt(room)
        addLine(head)
        addFragments(fragments, tail)
      }
    }

    addFragments(fragments.tail, fragments.head)

    val result = frags.result
    if(!result.isEmpty) {
      lines += new Line(result, List.empty)
    }

    lines.result
  }
}

object EmptyLine extends Line(List.empty, List.empty)

sealed trait TextState
case object TextStateStream extends TextState
case object TextStateColor extends TextState
case object TextStateEscape extends TextState

class Text {

  import Text.log
  private val lines = scala.collection.mutable.Map[Long, Line]()
  private var botLine : Long = 0

  lines(botLine) = EmptyLine

  def getWrapLines(numLines: Int, wrapAt: Int, botLine: Long) = synchronized {

    val bl = if(botLine == -1) this.botLine else botLine

    @tailrec
    def get(numLines: Int, lineNum: Long, rv: List[Line] = List.empty): List[Line] = {
      if(numLines <= 0 || lineNum < 0) {
        rv
      } else {
        val toadd = lines.getOrElse(lineNum, EmptyLine).mergeCommands.split(wrapAt)
        get(numLines - toadd.size, lineNum - 1, toadd ++ rv)
      }
    }

    val rv = get(numLines, bl)
    rv.drop(rv.length - numLines)
  }

  def length = synchronized(botLine)

  def addCommand(lineNum: Long, cmd: String) = synchronized {
    val line = lines(lineNum)
    lines(lineNum) = line.copy(commands = line.commands :+ cmd)
  }

  def setLine(lineNum: Long, txt: String) = synchronized {
    if (txt.contains("\n")) throw new Exception("text should not contain newline")

    var colorCode : ColorCode = DefaultColorCode

    val color = StringBuilder.newBuilder
    val text = StringBuilder.newBuilder
    var state : TextState = TextStateStream

    val fragments = List.newBuilder[Fragment]

    def addFragment : Unit = {
      if (text.size > 0) {
        fragments +=  Fragment(text.result(), colorCode)
        text.clear
      }
    }

    txt.getBytes.foreach { b =>
      state match {
        case TextStateStream =>
          if (b == 27.toByte) {
            addFragment
            state = TextStateEscape
          } else if (b != '\r') {
            text += b.toChar
          }

        case TextStateColor =>
          if (b == 'm') {
            colorCode = setColor(color.result(), colorCode)
            color.clear
            state = TextStateStream
          } else {
            color += b.toChar
          }

        case TextStateEscape =>
          state = if (b == '[') {
            TextStateColor
          } else TextStateStream
      }
    }

    addFragment

    if (lines.size > lineNum) {
      lines(lineNum) = lines(lineNum).copy(fragments = fragments.result())
    } else lines(lineNum) = Line(fragments.result(), List.empty)

    botLine = Math.max(botLine, lineNum)
  }

  private def setColor(s: String, colorCode: ColorCode) : ColorCode = {

    if (s == "0") return DefaultColorCode

    Try {
      var fg = colorCode.fg
      var bg = colorCode.bg
      var bold = colorCode.bold

      s.split(";").map(_.toInt).foreach { i =>
        i match {
          case 0 => bold = false
          case 1 => bold = true

          case 30 => fg = TelnetColorBlack
          case 31 => fg = TelnetColorRed
          case 32 => fg = TelnetColorGreen
          case 33 => fg = TelnetColorYellow
          case 34 => fg = TelnetColorBlue
          case 35 => fg = TelnetColorMagenta
          case 36 => fg = TelnetColorCyan
          case 37 => fg = TelnetColorWhite

          case 39 => fg = TelnetColorDefaultFg

          case 40 => bg = TelnetColorBlack
          case 41 => bg = TelnetColorRed
          case 42 => bg = TelnetColorGreen
          case 43 => bg = TelnetColorYellow
          case 44 => bg = TelnetColorBlue
          case 45 => bg = TelnetColorMagenta
          case 46 => bg = TelnetColorCyan
          case 47 => bg = TelnetColorWhite

          case 49 => bg = TelnetColorDefaultBg
        }
      }

      ColorCode(fg, bg, bold)
    } match {
      case Success(cc) => cc
      case Failure(e) =>
        log.error(s"error parsing color code $s", e)
        DefaultColorCode
    }
  }
}

object Text {
  val log = Logger(LoggerFactory.getLogger(Text.getClass))
}

class TextArea(val text: Text) extends JPanel {
  private var colorScheme : ColorScheme = DefaultColorScheme
  private var fontWidth = 0
  private var fontHeight = 0
  private var fontDescent = 0
  private var wrapAt = 100
  private var botLine : Long = -1

  setBackground(colorScheme.color(TelnetColorDefaultBg))
  setFocusable(false)

  def setColorScheme(colorScheme: ColorScheme): Unit = {
    this.colorScheme = colorScheme
    setBackground(colorScheme.color(TelnetColorDefaultBg))
    repaint()
  }

  def setBotLine(botLine: Long): Unit = {
    this.botLine = botLine
    repaint()
  }

  def setWrap(wrapAt : Int) : Unit = {
    this.wrapAt = wrapAt
    repaint()
  }

  override def setFont(font: Font) : Unit = {
    super.setFont(font)

    val bf = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val metrics = bfg.getFontMetrics(font)

    fontWidth = metrics.stringWidth("a")
    fontHeight = metrics.getHeight
    fontDescent = metrics.getDescent

    repaint()
  }

  override def paint(g: Graphics): Unit = {
    val ts = System.currentTimeMillis
    super.paint(g)

    val height = g.getClipBounds.height - 5
    val numLines = Math.ceil(height.toDouble / fontHeight).toInt
    val linesToDraw = text.getWrapLines(numLines, wrapAt, botLine)

    @tailrec
    def drawLine(fragments: List[Fragment], x: Int, y: Int): Unit = {
      fragments match {
        case Nil =>
        case head :: xs =>
          val width = fontWidth*head.text.length

          if (head.colorCode.bg != TelnetColorDefaultBg) {
            g.setColor(head.colorCode.bgColor(colorScheme))
            g.fillRect(x, y - fontHeight, width, fontHeight)
          }

          g.setColor(head.colorCode.fgColor(colorScheme))
          g.drawString(head.text, x, y - fontDescent)

          drawLine(xs, x + width, y)
      }
    }

    linesToDraw.reverse.zipWithIndex.foreach {
      case (line, index) => drawLine(line.fragments, 5, height - index * fontHeight)
    }

//    println("took "+(System.currentTimeMillis() - ts) + " millis")
  }
}

class SplittableTextArea(text: Text) extends JSplitPane with MouseWheelListener {
  private val topTextArea = new TextArea(text)
  private val textArea = new TextArea(text)
  private var scrollPos : Long = 0
  private var scrollSpeed = 4

  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topTextArea)
  setBottomComponent(textArea)
  setFocusable(false)

  addMouseWheelListener(this)

  unsplit

  textArea.setVisible(true)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  override def setFont(font: Font): Unit = {
    super.setFont(font)
    topTextArea.setFont(font)
    textArea.setFont(font)
  }

  def unsplit(): Unit = {
    topTextArea.setVisible(false)
    setDividerSize(0)
    setDividerLocation(0)
  }

  def isSplit = topTextArea.isVisible

  def split : Unit = {
    scrollPos = text.length
    topTextArea.setBotLine(scrollPos)
    setDividerLocation(0.7)
    setDividerSize(4)
    topTextArea.setVisible(true)
  }

  def handleDown : Unit = {
    if(!isSplit) return
    val maxPos = text.length
    scrollPos += scrollSpeed

    if (scrollPos >= maxPos) unsplit else topTextArea.setBotLine(scrollPos)
  }

  def handleUp : Unit = {
    if(!isSplit) split else {
      scrollPos = Math.max(scrollPos - scrollSpeed, 1)
      topTextArea.setBotLine(scrollPos)
    }
  }

  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    if(e.getWheelRotation < 0) handleUp else handleDown
    e.consume()
  }

}
