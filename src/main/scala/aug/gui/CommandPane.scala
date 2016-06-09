package aug.gui

import java.awt.event.{ComponentEvent, ComponentListener}
import java.awt.{BorderLayout, Color, Component, EventQueue, Graphics}
import javax.swing.JPanel

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

object CommandPane {
  val log = Logger(LoggerFactory.getLogger(CommandPane.getClass))
}

class CommandPane(private var centerComponent: Resizer) extends JPanel with Resizer with ComponentListener {

  import CommandPane._

  val commandLine = new CommandLine

  setLayout(null)
  centerComponent.setVisible(true)
  add(centerComponent)
  add(commandLine,BorderLayout.SOUTH)
  setBackground(Color.BLACK)
  setVisible(false)

  MainWindow.register(this)

  def setCenterComponent(centerComponent: Resizer) : Unit = {
    log.debug("new center component {}",centerComponent)
    remove(centerComponent)
    add(centerComponent)
    this.centerComponent = centerComponent
    resize
  }


  def raise(): Unit = {
    log.trace("raising")
    EventQueue.invokeLater(new Runnable() {
      override def run(): Unit = {
        MainWindow.toFront
        MainWindow.repaint()
        commandLine.requestFocusInWindow
      }
    })
  }

  def resize : Unit = {
    log.trace("resizing")

    val p = getParent
    if(p==null) return


    val th = MainTabbedPane.getUI.getTabBounds(MainTabbedPane,MainTabbedPane.getSelectedIndex).height
    val h = p.getHeight
    val rh = p.getHeight - th
    val w = p.getWidth
    setBounds(0, th, w, rh)
    setBackground(Color.RED)

    val ch = 45
    commandLine.setBounds(0,h-ch,getWidth,ch)
    val sh = h-ch
    centerComponent.setBounds(0,0,getWidth,sh)
    centerComponent.resize
  }

  override def paint(g: Graphics) = {
    resize
    super.paint(g)
  }

  override def componentShown(e: ComponentEvent): Unit = {}

  override def componentHidden(e: ComponentEvent): Unit = {}

  override def componentMoved(e: ComponentEvent): Unit = {}

  override def componentResized(e: ComponentEvent): Unit = {
    resize
  }
}
