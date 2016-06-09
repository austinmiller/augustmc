package aug.gui

import java.awt.{Color, EventQueue}
import java.awt.event.{ActionEvent, ActionListener, MouseAdapter, MouseEvent, MouseListener}
import javax.swing.{JFrame, JMenuItem, JPanel, JPopupMenu, JSplitPane}

import scala.util.Random

/**
  * Created by austin on 6/8/2016.
  */
class SplittablePanel extends JPanel with MouseListener {

  val r= new Random()

  setBackground(new Color(r.nextInt(256),r.nextInt(256),r.nextInt(256)))
  addMouseListener(this)

  def splitLeft = {
    println("split")
    val p = getParent
    p.remove(this)
    val sp = new SplitPanel(JSplitPane.HORIZONTAL_SPLIT)
    val newspl = new SplittablePanel
    p.add(sp)
    sp.setLeftComponent(this)
    sp.setRightComponent(newspl)
    sp.setDividerLocation(0.5)
    SplittablePanel.repaint()
  }

  def popup = {
    val m = new JPopupMenu("popup")
    val mi = new JMenuItem("left")
    mi.addActionListener(new ActionListener() {
      override def actionPerformed(e: ActionEvent): Unit = splitLeft
    })
    m.add(mi)
    m
  }

  override def mouseExited(e: MouseEvent): Unit = {}

  override def mouseClicked(e: MouseEvent): Unit = {}

  override def mouseEntered(e: MouseEvent): Unit = {}

  override def mousePressed(e: MouseEvent): Unit = {
    if(e.isPopupTrigger) {
      popup.show(e.getComponent,e.getX,e.getY)
    }
  }

  override def mouseReleased(e: MouseEvent): Unit = {
    if(e.isPopupTrigger) {
      popup.show(e.getComponent,e.getX,e.getY)
    }
  }
}

class SplitPanel(val orient: Int) extends JSplitPane {

  setOrientation(orient)

  setDividerSize(4)
//
//  override def getDividerLocation = getParent.getWidth / 2

}

object SplittablePanel extends JFrame {

  add(new SplittablePanel)

  def setup = {
    setTitle("Title")
    setSize(800,600)
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    setVisible(true)
  }

  def main(args: Array[String]) : Unit = {
    EventQueue.invokeLater(new Runnable() {def run = SplittablePanel.setup})
  }
}
