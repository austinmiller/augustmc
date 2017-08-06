package aug.gui

import java.awt.datatransfer.StringSelection
import java.awt.event._
import java.awt.image.BufferedImage
import java.awt.{Font, Graphics, Toolkit}
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JSplitPane}

import aug.io._
import aug.profile.{ConfigManager, ProfileConfig}
import aug.script.framework.TextWindowInterface

import scala.annotation.tailrec

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

    clipHeight = getHeight
    clipWidth = getWidth
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

  def setHighlightable(highlightable: Boolean): Unit = {
    if (highlightable) {
      if (!getMouseListeners.contains(mouse)) {
        addMouseListener(mouse)
        addMouseMotionListener(motionListener)
        anchor = None
        highlightTo = None
        repaint()
      }
    } else {
      if (getMouseListeners.contains(mouse)) {
        removeMouseListener(mouse)
        removeMouseMotionListener(motionListener)
        anchor = None
        highlightTo = None
        repaint()
      }
    }
  }

  addMouseListener(mouse)
  addMouseMotionListener(motionListener)
}

class SplittableTextArea(profileConfig: ProfileConfig, hasHighlight: HasHighlight, console: Boolean = true)
  extends JSplitPane with MouseWheelListener with TextWindowInterface {
  val text = new Text(profileConfig)
  private val topTextArea = new TextArea(hasHighlight, text)
  private val textArea = new TextArea(hasHighlight, text)
  private var scrollPos : Long = 0
  private var scrollSpeed = 4
  private var splittable = true

  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topTextArea)
  setBottomComponent(textArea)
  setFocusable(false)

  addMouseWheelListener(this)

  unsplit()

  textArea.setVisible(true)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  def setProfileConfig(profileConfig: ProfileConfig): Unit = text.profileConfig = profileConfig

  def setActiveFont(font: Font): Unit = {
    setFont(font)
    topTextArea.setActiveFont(font)
    textArea.setActiveFont(font)
  }

  override def unsplit(): Unit = {
    if (isSplit) {
      topTextArea.setVisible(false)
      setDividerSize(0)
      setDividerLocation(0)
    }
  }

  def isSplit: Boolean = topTextArea.isVisible

  override def split() : Unit = {
    if (!isSplit && splittable) {
      scrollPos = text.length
      topTextArea.setBotLine(scrollPos)
      setDividerLocation(0.7)
      setDividerSize(4)
      topTextArea.setVisible(true)
    }
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
    if (!console) {
      text.addLine(line)
      repaint()
    }
  }

  override def clear(): Unit = {
    if (!console) {
      text.clear()
      repaint()
    }
  }

  override def setSplittable(splittable: Boolean): Unit = {
    this.splittable = splittable
    if (!splittable) unsplit()
  }

  override def setLine(lineNum: Long, line: String): Unit = {
    if (!console) {
      text.setLine(lineNum, line)
      repaint()
    }
  }

  override def setHighlightable(highlightable: Boolean): Unit = {
    textArea.setHighlightable(highlightable)
    topTextArea.setHighlightable(highlightable)
  }

  private def getColorScheme(colorSchemeName: String): Option[ColorScheme] = {
    ConfigManager.getMainConfig.colorSchemes.find(_.name == colorSchemeName).map(new ConfigurableColorScheme(_))
  }

  override def setColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach {cs =>
      textArea.setColorScheme(cs)
      topTextArea.setColorScheme(cs)
    }
  }

  override def setTextFont(font: Font): Unit = if (!console) setActiveFont(font)

  override def setTopColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach {cs =>
      topTextArea.setColorScheme(cs)
    }
  }

  override def setBottomColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach {cs =>
      textArea.setColorScheme(cs)
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