package aug.gui

import java.awt.{BorderLayout, Color, EventQueue, Font, Graphics}
import java.awt.event.{ComponentEvent, ComponentListener, MouseWheelEvent, MouseWheelListener}
import java.awt.image.BufferedImage
import javax.swing._

import aug.util.{LoremIpsum, Util}
import com.google.common.base.Splitter
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

case class ColoredText(text: String, color: Color = Color.WHITE, background: Option[Color] = None)

class TextLine(texts: ArrayBuffer[ColoredText], drawBorder: Boolean = false) {
  import TextPanel._

  def this(s: String) {
    this(ArrayBuffer(ColoredText(s)))
  }

  val length : Int = texts map {_.text.length} sum
  val bf = new BufferedImage(1+fontWidth*length,fontHeight,BufferedImage.TYPE_INT_ARGB)
  var next : TextLine = null
  var prev : TextLine = null
  private val bfg = bf.createGraphics()
  bfg.setFont(TextPanel.font)

  {
    var pos = 0
    texts foreach { ct =>
      bfg.setColor(ct.color)
      bfg.drawString(ct.text, pos*fontWidth, fontHeight-fontDescent)

      pos += ct.text.length
    }

    if(drawBorder) {
      val w = bf.getWidth
      val h = bf.getHeight
      bfg.setColor(Color.WHITE)
      bfg.drawLine(0,0,w,0)
      bfg.drawLine(0,h-1,w,h-1)
      bfg.drawLine(0,0,0,h)
      bfg.drawLine(w-1,0,w-1,h)
    }

  }



  def height(): Int = {
    bf.getHeight()
  }

}


object TextPanel {
  val log = Logger(LoggerFactory.getLogger(TextPanel.getClass))

  val font = new Font( "Monospaced", Font.PLAIN, 20 )

  val (fontWidth,fontHeight,fontDescent) = {
    val bf = new BufferedImage(200,80,BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val metrics = bfg.getFontMetrics(font)

    (metrics.stringWidth("a"),metrics.getHeight,metrics.getDescent)
  }

  val colorMap = Map(
    "[0" -> new Color(170,170,170),

    "[0;30" -> new Color(85,85,85),
    "[0;31" -> new Color(170,0,0),
    "[0;32" -> new Color(0,170,0),
    "[0;33" -> new Color(170,85,0),
    "[0;34" -> new Color(0,0,170),
    "[0;35" -> new Color(170,0,170),
    "[0;36" -> new Color(0,170,170),
    "[0;37" -> new Color(170,170,170),

    "[1;30" -> new Color(128,128,128),
    "[1;31" -> new Color(255,0,0),
    "[1;32" -> new Color(0,255,0),
    "[1;33" -> new Color(255,255,0),
    "[1;34" -> new Color(0,0,255),
    "[1;35" -> new Color(255,0,255),
    "[1;36" -> new Color(0,255,255),
    "[1;37" -> new Color(255,255,254) // 254 because of java 1.8 linux bug

  )

}

trait TextReceiver {
  def addText(txt: String)
  def setCurrentColor(color: Color)
  def info(txt: String, color: Option[Color] = None)
  def echo(txt: String, color: Option[Color] = None)
}

sealed trait ColorState
case object Stream extends ColorState
case object ColorCodeState extends ColorState

class TextPanel extends JPanel with TextReceiver {

  import TextPanel._

  setBackground(Color.BLACK)

  @volatile
  var top : TextLine = new TextLine(Util.fullName)
  var scrollBot = top
  var bot = top

  var lines = 0
  var maxLines = 1000

  var texts = ArrayBuffer[ColoredText]()

  private var color = Color.WHITE

  private def addLine(tl: TextLine) = {
    tl.next = top
    top.prev = tl
    top = tl
    scrollBot = top

    if(lines == maxLines) {
      bot = bot.prev
      bot.next = null
    } else {
      lines += 1
    }
  }



  override def info(txt: String, color: Option[Color] = None) = synchronized {
    val c = color getOrElse Color.YELLOW
    addLine(new TextLine(ArrayBuffer[ColoredText](ColoredText(txt,c))))
  }

  override def addText(txt: String) : Unit = synchronized {
    var colorCode = StringBuilder.newBuilder
    var text = StringBuilder.newBuilder
    var state : ColorState = Stream

    txt.getBytes.foreach {b =>
      state match {
        case Stream =>
          if(b==27.toByte) {
            addSameColorText(text.result)
            text.clear
            state = ColorCodeState
          } else {
            text += b.toChar
          }
        case ColorCodeState =>
          if(b == 'm'.toByte) {
            setCurrentColor(colorCode.result)
            colorCode.clear
            state = Stream
          } else {
            colorCode += b.toChar
          }
      }
    }

    val s = text.result
    if(s.length > 0) addSameColorText(s)

  }
  private def addSameColorText(txt: String) : Unit = {
    import scala.collection.JavaConversions._
    val lines = Splitter.on("\n").split(txt).toArray
    if(lines.length > 1) {
      lines.take(lines.size - 1).foreach { l =>
        texts += ColoredText(l,color)
        addLine(new TextLine(texts))
        texts = ArrayBuffer[ColoredText]()
      }
    }

    if(lines.last.length > 0) texts += ColoredText(lines.last,color)

    this.repaint()
  }

  private def setCurrentColor(code: String) :Unit = {
    colorMap.get(code) map { c => setCurrentColor(c)}
  }

  override def setCurrentColor(color: Color) = synchronized {
    this.color = color
  }

  def clear : Unit = synchronized {
    top = new TextLine("")
    texts = ArrayBuffer[ColoredText]()
    lines = 0
  }

  def scrollUp(lines: Int) : Unit = {
    for(i <- 0 to lines) {
      if(scrollBot.next != null) scrollBot = scrollBot.next
    }

    repaint()
  }

  def scrollDown(lines: Int) : Unit = {
    for(i <- 0 to lines) {
      if(scrollBot.prev != null && scrollBot != top) scrollBot = scrollBot.prev
    }
    repaint()
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)

    val t = System.currentTimeMillis

    var pos = g.getClipBounds.height - 5

    var cur = scrollBot


    if(texts.size > 0) {
      val tl = new TextLine(texts)
      pos -= tl.height
      g.drawImage(tl.bf, 5, pos, null)
    }

    while(cur != null &&  pos > 0) {
      pos -= cur.height
      g.drawImage(cur.bf, 5, pos, null)
      cur = cur.next
    }
    val rt = System.currentTimeMillis() - t

    if(rt>500) {
      log.debug(s"long render time $rt")
    }
  }

  def resize = {}

  override def echo(txt: String, color: Option[Color] = None): Unit = {
    val lc = this.color
    color map { c=> this.color = c }
    addText(txt)
    this.color = lc

  }
}

class SplitTextPanel extends JSplitPane with MouseWheelListener with TextReceiver with Resizer {
  private val topPanel = new TextPanel
  private val textPanel = new TextPanel

  topPanel.setBackground(new Color(15,15,15))
  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topPanel)
  setBottomComponent(textPanel)
  textPanel.setVisible(true)

  addMouseWheelListener(this)
  SplitTextPanel.addMouseWheelListener(this)

  unsplit

  def unsplit(): Unit = {
    setDividerSize(0)
    setDividerLocation(0)
    topPanel.setVisible(false)
  }

  def isSplit = topPanel.isVisible

  def split : Unit = {
    topPanel.top = textPanel.top
    topPanel.bot = textPanel.bot
    topPanel.lines = textPanel.lines
    topPanel.scrollBot = topPanel.top
    topPanel.setVisible(true)
    setDividerLocation(0.7)
    setDividerSize(4)
  }

  def handleDown(notches: Int) : Unit = {
    if(!isSplit) return
    topPanel.scrollDown(SplitTextPanel.NOTCHES)
    if(topPanel.top == topPanel.scrollBot) unsplit
  }

  def handleUp(notches: Int) : Unit = {
    if(!isSplit) split else topPanel.scrollUp(SplitTextPanel.NOTCHES)
  }

  override def getDividerLocation(): Int = getParent.getWidth / 2
  override def getLastDividerLocation(): Int = getDividerLocation
  override def resize : Unit = setDividerLocation(getDividerLocation)

  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    val notches = e.getWheelRotation
    if(notches < 0) handleUp(notches) else handleDown(notches)
    e.consume()
  }

  override def addText(txt: String) : Unit = textPanel.addText(txt)
  override def setCurrentColor(color: Color) = textPanel.setCurrentColor(color)
  override def info(txt: String, color: Option[Color] = None): Unit = textPanel.info(txt,color)
  override def echo(txt: String, color: Option[Color] = None): Unit = textPanel.echo(txt,color)
}

object SplitTextPanel extends JFrame with ComponentListener {

  val NOTCHES = 25
  val spt = new SplitTextPanel

  def setup = {
    setBackground(Color.RED)

    add(spt, BorderLayout.CENTER)
    setTitle("title")
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setVisible(true)
    setSize(800,600)
    spt.repaint()
  }

  def write(text: String, color: Color = Color.WHITE, wait: Long = 0) = {
    spt.setCurrentColor(color)
    spt.addText(text)
    Thread.sleep(wait)
  }

  def main(args: Array[String]): Unit = {
    EventQueue.invokeLater(new Runnable() { def run = SplitTextPanel.setup})

    val colors = List(Color.BLUE,Color.CYAN,Color.RED,Color.WHITE,Color.DARK_GRAY,Color.MAGENTA,Color.YELLOW,Color.PINK,Color.GREEN)

    val rand = new Random()

    def rcolor = colors(rand.nextInt(colors.size))

    val speed = 20

    for(i <- 0 to 10000) {
      if(rand.nextBoolean() == true) {
        for(j <- 0 to 6) {
          write(LoremIpsum.words(1) + " ",rcolor,rand.nextInt(speed/6))
        }
      } else {
        write(LoremIpsum.sentence, rcolor, rand.nextInt(speed))
      }
      write(LoremIpsum.sentence + LoremIpsum.sentence,rcolor)
      write("\n")
    }
  }

  override def componentShown(e: ComponentEvent): Unit = {}

  override def componentHidden(e: ComponentEvent): Unit = {}

  override def componentMoved(e: ComponentEvent): Unit = {}

  override def componentResized(e: ComponentEvent): Unit = { spt.resize }
}
