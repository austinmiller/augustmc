import java.awt.image.BufferedImage
import java.awt.{Color, EventQueue, Font, Graphics}
import javax.swing.{JFrame, JPanel}

import scala.util.Random


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

  private var top : TextLine = new TextLine("August MC")

  private var count : Long = 0;
  var renderTime : Long = 0;

  def add(txt: String) : Unit = {
    val tl = new TextLine(txt + " " + (renderTime/count))
    tl.next = top
    top = tl
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
      val ct = System.currentTimeMillis()
      pos -= cur.getHeight
      g.drawImage(cur.bf, 5, pos, null)
      cur = cur.next
      renderTime += System.currentTimeMillis() - ct
      count += 1
    }
  }
}

class PFrame extends JFrame {

  val tp = new TextPanel

  add(tp)

  def start = {
    setTitle("Panel Test")
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setSize(800, 600)
    setVisible(true)
  }
}

object PanelTest {
  def main(args: Array[String]): Unit = {

    val random = new Random
    val pf = new PFrame()

    EventQueue.invokeLater(new Runnable() {

      @Override
      def run() {
        pf.start
      }
    });

    for(i <- 1 to 100000) {
      pf.tp.add(random.nextInt + "")
      pf.repaint()
      Thread.sleep(5)
    }
  }

}
