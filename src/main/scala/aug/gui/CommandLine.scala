package aug.gui

import java.awt.Font
import java.awt.event.{KeyEvent, KeyListener}
import javax.swing.{BorderFactory, JTextArea}

import aug.io.SidePanelColor
import aug.util.RingBuffer
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

object CommandLine {
  val log = Logger(LoggerFactory.getLogger(CommandLine.getClass))
}


class CommandLine extends JTextArea with KeyListener {

  val log = CommandLine.log

//  private val listeners = ListBuffer[CommandLineListener]()
  val history = new RingBuffer[String](20)
  var historyIndex = -1

  setFont(new Font("Courier New", Font.PLAIN, 20))

  setLineWrap(true)
  setWrapStyleWord(true)

  setBorder(BorderFactory.createLineBorder(SidePanelColor, 3))

  addKeyListener(this)

//  def addCommandLineListener(listener: CommandLineListener) = listeners += listener

  def process(e: KeyEvent) {
    if(!e.isConsumed || e.getComponent.equals(this)) return

    processKeyEvent(e)
  }

  def execute(msg: String) = {
    log.trace("executing: {}",msg)

    if(history(0) != msg) history.push(msg)
    historyIndex = -1

//    listeners.foreach{_.execute(msg)}
  }

  override def keyTyped(e: KeyEvent): Unit = {}

  override def keyPressed(e: KeyEvent): Unit = {
    e.getKeyCode match {
      case KeyEvent.VK_UP => historyUp
      case KeyEvent.VK_DOWN => historyDown
      case KeyEvent.VK_ENTER =>
        e.consume()
        selectAll
        execute(getText)
      case _ =>
    }
  }

  def historyUp = {
    historyIndex += 1
    if(historyIndex == history.capacity) historyIndex = 0
    setText(history(historyIndex))
    selectAll
  }

  def historyDown = {
    historyIndex -= 1
    if(historyIndex < 0) historyIndex = history.capacity-1
    setText(history(historyIndex))
    selectAll
  }

  override def keyReleased(e: KeyEvent): Unit = {}
}
