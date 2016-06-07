package aug.gui

import java.awt.{Color, Font, Graphics}
import java.awt.image.BufferedImage
import javax.swing.JPanel

class TextLine(text: String) {

  val bf = new BufferedImage(TextPanel.fontWidth*80,TextPanel.fontHeight,BufferedImage.TYPE_INT_RGB)
  var next : TextLine = null;
  private val bfg = bf.createGraphics()
  bfg.setFont(TextPanel.font)

  bfg.drawString(text,0,TextPanel.fontHeight)

  def getHeight(): Int = {
    bf.getHeight()
  }

}


object TextPanel {
  val font = new Font( "Monospaced", Font.PLAIN, 16 )

  val (fontWidth,fontHeight) = {
    val bf = new BufferedImage(200,80,BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val metrics = bfg.getFontMetrics(font)
    (metrics.stringWidth("a"),metrics.getHeight)
  }

}

class TextPanel extends JPanel {
  setBackground(Color.BLACK)

  @volatile
  private var top : TextLine = new TextLine("August MC")

  def add(txt: String) : Unit = {
    val tl = new TextLine(txt)
    tl.next = top
    top = tl
    this.repaint()
  }

  def replace(tl: TextLine) : Unit = {
    tl.next = top.next
    top = tl
  }

  override def paint(g: Graphics): Unit = {
    super.paint(g)
    var pos = g.getClipBounds.height - 5

    var cur = top

    while(cur != null &&  pos > 0) {
      pos -= cur.getHeight
      g.drawImage(cur.bf, 5, pos, null)
      cur = cur.next
    }
  }

  def resize : Unit = {

  }
}