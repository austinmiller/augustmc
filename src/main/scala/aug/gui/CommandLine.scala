package aug.gui

import java.awt.Font
import java.awt.event.{ActionEvent, KeyEvent, KeyListener}
import javax.swing._
import javax.swing.event.CaretEvent

import aug.io.SidePanelColor
import aug.profile.{Profile, UserCommand}
import aug.misc.RingBuffer
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

object CommandLine {
  val log = Logger(LoggerFactory.getLogger(CommandLine.getClass))
}

class CommandLine(profile: Profile) extends JTextArea with KeyListener {

  private val log = CommandLine.log

  private val history = new RingBuffer[String](20)
  private var historyIndex = -1

  setFont(new Font("Courier New", Font.PLAIN, 20))

  setLineWrap(true)
  setWrapStyleWord(true)

  setBorder(BorderFactory.createLineBorder(SidePanelColor, 3))

  addKeyListener(this)
  addKeyListener(profile.shiftListener)

  // we don't want this component stealing copy from the menu item
  getActionMap.put("copy-to-clipboard", new AbstractAction() {
    override def actionPerformed(e: ActionEvent): Unit = profile.copyText()
  })

  // prevent this thing from beeping, ugh
  private val deleteAction = getActionMap.get("delete-previous")
  getActionMap.put("delete-previous", new AbstractAction() {
    override def actionPerformed(e: ActionEvent): Unit = {
      if (getText.nonEmpty) {
        deleteAction.actionPerformed(e)
      }
    }
  })

  def process(e: KeyEvent) {
    if(!e.isConsumed || e.getComponent.equals(this)) return

    processKeyEvent(e)
  }

  private def execute(msg: String): Unit = {
    log.trace("executing: {}",msg)

    if(history(0) != msg) history.push(msg)
    historyIndex = -1

    profile.offer(UserCommand(msg))
  }

  override def keyTyped(e: KeyEvent): Unit = {}

  override def keyPressed(e: KeyEvent): Unit = {
    e.getKeyCode match {
      case KeyEvent.VK_UP => historyUp()
      case KeyEvent.VK_DOWN => historyDown()
      case KeyEvent.VK_ENTER =>
        e.consume()
        selectAll()
        execute(getText)
      case _ =>
    }
  }

  def historyUp(): Unit = {
    historyIndex += 1
    if(historyIndex == history.capacity) historyIndex = 0
    setText(history(historyIndex))
    selectAll()
  }

  def historyDown(): Unit = {
    historyIndex -= 1
    if(historyIndex < 0) historyIndex = history.capacity-1
    setText(history(historyIndex))
    selectAll()
  }

  override def keyReleased(e: KeyEvent): Unit = {}

  addCaretListener((e: CaretEvent) => {
    if (e.getDot != e.getMark) {
      profile.highlight = Some(getSelectedText)
    }
  })
}
