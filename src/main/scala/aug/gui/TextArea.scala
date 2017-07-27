package aug.gui

import java.awt.datatransfer.{Clipboard, StringSelection}
import java.awt.event._
import java.awt.image.BufferedImage
import java.awt.{Font, Graphics, Toolkit}
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JSplitPane}

import aug.io._
import aug.script.shared.TextWindowInterface
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

case class Fragment(text: String, colorCode: ColorCode) {
  def splitAt(index: Int) : (Fragment, Fragment) = {
    (Fragment(text.substring(0, index), colorCode), Fragment(text.substring(index), colorCode))
  }

  def highlight: Fragment = this.copy(colorCode = colorCode.copy(bg = TelnetColorBlue))
}

case class Line(fragments: List[Fragment], commands: List[String], lineNum: Long, pos: Int = 0) {
  def :+(fragment: Fragment) = {
    if (fragments.nonEmpty && fragments.last.colorCode == fragment.colorCode) {
      appendToLastFragment(fragment.text)
    } else Line(fragments :+ fragment, commands, lineNum, pos)
  }

  private def appendToLastFragment(text: String) = {
    val last = fragments.last
    Line(fragments.dropRight(1) :+ Fragment(last.text + text, last.colorCode), commands, lineNum, pos)
  }

  def mergeCommands : Line = {
    if (commands.isEmpty) this else {
      val frag = Fragment(commands.mkString(" | "), ColorCode(TelnetColorYellow))
      this.copy(fragments = this.fragments :+ frag)
    }
  }

  def length = fragments.map(_.text.length).sum

  def split(wrapAt: Int) : List[Line] = {

    if (fragments.isEmpty) {
      return List[Line](this)
    }

    val lines = List.newBuilder[Line]
    val frags = List.newBuilder[Fragment]

    def addLine(fragment: Fragment, pos: Int): Int = {
      frags += fragment
      val newLine = Line(frags.result, List.empty, lineNum, pos)
      lines += newLine
      frags.clear
      pos + newLine.length
    }

    @tailrec
    def addFragments(fragments: List[Fragment], fragment: Fragment, pos: Int, room: Int = wrapAt) : Int = {
      if (fragment.text.length == room) {
        val newPos = addLine(fragment, pos)
        if (fragments != Nil) addFragments(fragments.tail, fragments.head, newPos) else pos
      } else if (fragment.text.length < room) {
        frags += fragment
        if (fragments != Nil) addFragments(fragments.tail, fragments.head, pos, room - fragment.text.length) else pos
      } else {
        val (head, tail) = fragment.splitAt(room)
        val newPos = addLine(head, pos)
        addFragments(fragments, tail, newPos)
      }
    }

    val pos = addFragments(fragments.tail, fragments.head, 0)

    val result = frags.result
    if(result.nonEmpty) {
      lines += Line(result, List.empty, lineNum, pos)
    }

    lines.result
  }

  def str = fragments.map(_.text).mkString

  def highlight(start: TextPos, end: TextPos): Line = {
    if (lineNum >= start.lineNum && lineNum <= end.lineNum) {
      var fragbuilder = List.newBuilder[Fragment]

      @tailrec
      def handleFragment(list: List[Fragment], pos: Int): Unit = {
        list match {
          case xs :: tail =>
            val fragStart = TextPos(lineNum, pos)
            // fragEnd is up to but not including, it's the starting pos of the next frag (if there is one)
            val fragEnd = TextPos(lineNum, xs.text.length + pos - 1)

            if (start <= fragEnd && start > fragStart) {
              val (keep, remainder) = xs.splitAt(start.pos - pos)
              fragbuilder += keep
              handleFragment(remainder :: tail, pos + keep.text.length)
            } else {
              if (start <= fragStart && fragEnd <= end) {
                fragbuilder += xs.highlight
              } else if (start > fragEnd || end < fragStart) {
                fragbuilder += xs
              } else {
                // end slices the fragment and start doesn't
                val (keep, remainder) = xs.splitAt(end.pos - pos + 1)
                fragbuilder += keep.highlight
                fragbuilder += remainder
              }

              handleFragment(tail, fragEnd.pos + 1)
            }

          case Nil =>
        }
      }

      handleFragment(fragments, pos)

      this.copy(fragments = fragbuilder.result())
    } else this
  }
}

object EmptyLine {
  def apply(lineNum: Long): Line = Line(List(Fragment("", DefaultColorCode)), List.empty, lineNum)
}

sealed trait TextState
case object TextStateStream extends TextState
case object TextStateColor extends TextState
case object TextStateEscape extends TextState

class Text {

  import Text.log
  private val lines = scala.collection.mutable.Map[Long, Line]()
  private var botLine : Long = 0

  lines(botLine) = EmptyLine(botLine)

  def apply(lineNum: Long): Line = lines.getOrElse(lineNum, EmptyLine(lineNum))

  def getWrapLines(numLines: Int, wrapAt: Int, botLine: Long): List[Line] = synchronized {
    val bl = if(botLine == -1) this.botLine else botLine

    @tailrec
    def get(numLines: Int, lineNum: Long, rv: List[Line] = List.empty): List[Line] = {
      if(numLines <= 0 || lineNum < 0) {
        rv
      } else {
        val toadd = lines.getOrElse(lineNum, EmptyLine(lineNum)).mergeCommands.split(wrapAt)
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

  def addLine(txt: String) = synchronized {
    setLine(botLine + 1, txt)
  }

  def setLine(lineNum: Long, txt: String) = synchronized {
    if (txt.contains("\n")) throw new Exception("text should not contain newline")

    var colorCode : ColorCode = DefaultColorCode

    val color = StringBuilder.newBuilder
    val text = StringBuilder.newBuilder
    var state : TextState = TextStateStream

    val fragments = List.newBuilder[Fragment]

    def addFragment() : Unit = {
      if (text.nonEmpty) {
        fragments +=  Fragment(text.result(), colorCode)
        text.clear
      }
    }

    txt.getBytes.foreach { b =>
      state match {
        case TextStateStream =>
          if (b == 27.toByte) {
            addFragment()
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

    addFragment()

    if (lines.size > lineNum) {
      lines(lineNum) = lines(lineNum).copy(fragments = fragments.result())
    } else lines(lineNum) = Line(fragments.result(), List.empty, lineNum)

    botLine = Math.max(botLine, lineNum)
  }

  private def setColor(s: String, colorCode: ColorCode) : ColorCode = {

    if (s == "0") return DefaultColorCode

    Try {
      var fg = colorCode.fg
      var bg = colorCode.bg
      var bold = colorCode.bold

      s.split(";").map(_.toInt).foreach {
        case 0 => bold = false
        case 1 => bold = true

        case 2 => // draw feintly -- won't support yet
        case 4 => // draw underline -- won't support yet
        case 7 => // reverse fg/bg -- won't support yet

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

case class TextPos(lineNum: Long, pos: Int) extends Comparable[TextPos] {
  def <(o: TextPos) = this.compareTo(o) < 0
  def >(o: TextPos) = this.compareTo(o) > 0
  def <=(o: TextPos) = this.compareTo(o) <= 0
  def >=(o: TextPos) = this.compareTo(o) >= 0

  override def compareTo(o: TextPos): Int = {
    if (lineNum > o.lineNum) {
      1
    } else if (lineNum < o.lineNum) {
      -1
    } else {
      pos - o.pos
    }
  }
}

class TextArea(hasHighlight: HasHighlight, val text: Text) extends JPanel {
  private var colorScheme : ColorScheme = DefaultColorScheme
  private var fontWidth = 0
  private var fontHeight = 0
  private var fontDescent = 0
  private var wrapAt = 100
  private var numLines = 0
  private var numChars = 0
  private var clipHeight = 0
  private var clipWidth = 0
  private var botLine : Long = -1
  private var lines : Option[List[Line]] = None
  private var anchor : Option[TextPos] = None
  private var highlightTo : Option[TextPos] = None

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

  def setActiveFont(font: Font) : Unit = synchronized {
    setFont(font)

    val bf = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val metrics = bfg.getFontMetrics(font)

    fontWidth = metrics.stringWidth("a")
    fontHeight = metrics.getHeight
    fontDescent = metrics.getDescent

    repaint()
  }

  override def paint(g: Graphics): Unit = synchronized {
    super.paint(g)

    clipHeight = g.getClipBounds.height
    clipWidth = g.getClipBounds.width
    val height = clipHeight - 5
    val width = clipWidth - 5
    numLines = Math.ceil(height.toDouble / fontHeight).toInt
    numChars = Math.max(Math.floor(width.toDouble / fontWidth).toInt, 20)
    val linesToDraw = highlightLines(text.getWrapLines(numLines, numChars, botLine))
    lines = Some(linesToDraw)

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
  }

  private def textPos(x: Int, y: Int) = {
    lines.map{ ls =>
      val gy = (if (y >= clipHeight - 5) {
        numLines - 1
      } else {
        numLines - ((clipHeight - 5 - y) / fontHeight) - 1
      }) - numLines + ls.size

      val gx = if (x < 5) {
        0
      } else {
        (x-5) / fontWidth
      }

      val index = Math.max(0, gy)
      val line = ls(index)
      val pos = if(gy < 0) 0 else line.pos + Math.min(gx, line.length - 1)
      TextPos(line.lineNum, pos)
    }.get
  }

  private def highlightLines(lines: List[Line]): List[Line] = {
    highlight.map { hl=> lines.map(_.highlight(hl.head, hl.last)) }.getOrElse(lines)
  }

  private def highlight: Option[List[TextPos]] = {
    anchor.flatMap { a =>
      highlightTo.map { ht =>
        List(a, ht).sorted
      }
    }
  }

  private def highlightText: Option[String] = {
    highlight.map { l =>
      val l1 = l.head.lineNum
      val p1 = Math.max(0, l.head.pos)
      val l2 = l.last.lineNum
      val p2 = Math.max(0, l.last.pos + 1)

      if (l1 == l2) {
        text(l1).str.substring(p1, p2)
      } else {
        val lb = List.newBuilder[String]

        lb += text(l1).str.substring(p1)

        for (ln <- l.head.lineNum + 1 to l.last.lineNum - 1) {
          lb += text(ln).str
        }

        lb += text(l2).str.substring(0, p2)

        lb.result().mkString("\n")
      }
    }
  }

  val mouse = new MouseListener {
    override def mouseExited(e: MouseEvent): Unit = {}

    override def mouseClicked(e: MouseEvent): Unit = {
      anchor = None
      highlightTo = None
    }

    override def mouseEntered(e: MouseEvent): Unit = {}

    override def mousePressed(e: MouseEvent): Unit = {
      anchor = Some(textPos(e.getX, e.getY))
      highlightTo = Some(textPos(e.getX, e.getY))
    }

    override def mouseReleased(e: MouseEvent): Unit = {
      highlightTo = Some(textPos(e.getX, e.getY))
      hasHighlight.highlight = highlightText
      repaint()
    }
  }

  val motionListener = new MouseMotionListener {
    override def mouseMoved(e: MouseEvent): Unit = {}

    override def mouseDragged(e: MouseEvent): Unit = {
      highlightTo = Some(textPos(e.getX, e.getY))
      repaint()
    }
  }

  addMouseListener(mouse)
  addMouseMotionListener(motionListener)
}

class SplittableTextArea(hasHighlight: HasHighlight, echoable: Boolean = false) extends JSplitPane with
  MouseWheelListener with TextWindowInterface {
  val text = new Text
  private val topTextArea = new TextArea(hasHighlight, text)
  private val textArea = new TextArea(hasHighlight, text)
  private var scrollPos : Long = 0
  private var scrollSpeed = 4

  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topTextArea)
  setBottomComponent(textArea)
  setFocusable(false)

  addMouseWheelListener(this)

  unsplit()

  textArea.setVisible(true)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  def setActiveFont(font: Font): Unit = {
    setFont(font)
    topTextArea.setActiveFont(font)
    textArea.setActiveFont(font)
  }

  def unsplit(): Unit = {
    topTextArea.setVisible(false)
    setDividerSize(0)
    setDividerLocation(0)
  }

  def isSplit = topTextArea.isVisible

  def split() : Unit = {
    scrollPos = text.length
    topTextArea.setBotLine(scrollPos)
    setDividerLocation(0.7)
    setDividerSize(4)
    topTextArea.setVisible(true)
  }

  def handleDown() : Unit = {
    if(!isSplit) return
    val maxPos = text.length
    scrollPos += scrollSpeed

    if (scrollPos >= maxPos) unsplit() else topTextArea.setBotLine(scrollPos)
  }

  def handleUp() : Unit = {
    if(!isSplit) split() else {
      scrollPos = Math.max(scrollPos - scrollSpeed, 1)
      topTextArea.setBotLine(scrollPos)
    }
  }

  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    if(e.getWheelRotation < 0) handleUp() else handleDown()
    e.consume()
  }

  override def echo(line: String): Unit = {
    if (echoable) {
      text.addLine(line)
      repaint()
    }
  }
}

trait HasHighlight {
  var highlight: Option[String] = None
  def copyText(): Unit = {
    highlight.foreach{str=>
      Toolkit.getDefaultToolkit.getSystemClipboard.setContents(new StringSelection(str), null)
    }
  }
}