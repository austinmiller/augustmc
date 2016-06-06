package aug.gui

import java.awt.{Color, Font}
import java.awt.event.{KeyEvent, KeyListener}
import javax.swing.{BorderFactory, JTextArea}

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.reflect.ClassTag

object CommandLine {
  val log = Logger(LoggerFactory.getLogger(CommandLine.getClass))
}

class RingBuffer[A](val capacity: Int)(implicit m: ClassTag[A]) extends scala.collection.mutable.IndexedSeq[A] {
  private val data: Array[A] = new Array[A](length)
  private var index = length - 1
  private var nsize = 0

  def length = capacity

  private def off(idx: Int) : Int = (idx+index) % length

  def push(elem: A) : Unit = {
    index = (index + length -1) % length
    data(index) = elem
  }

  def apply(idx: Int) : A = {
    print(idx + " - "+ index + " ")
    if(idx < 0 || idx >= length) throw new IndexOutOfBoundsException
    data(off(idx))
  }

  override def update(idx: Int, elem: A): Unit = {
    if(idx < 0 || idx >= length) throw new IndexOutOfBoundsException
    data(off(idx)) = elem
  }
}

object RingBuffer {
  def main(args: Array[String]): Unit = {
    val s = new RingBuffer[String](5)
    for(i <- 0 to 6) {
      s foreach println
      println("")
      s.push(i+"")
    }
  }
}

class CommandLine extends JTextArea with KeyListener {
  val log = CommandLine.log


  private val listeners = ListBuffer[CommandLineListener]()
  val history = ArrayBuffer[String]()
  private var historyMax = 20
  private var historyIndex = -1

  setFont(new Font("Courier New", Font.PLAIN, 12))

  setBackground(Color.WHITE)
  setForeground(Color.BLACK)
  val border = BorderFactory.createLineBorder(Color.BLACK)
  setBorder(BorderFactory.createCompoundBorder(border,BorderFactory.createEmptyBorder(5,5,2,2)))

  MainWindow.register(this)
  requestFocusInWindow()
  addKeyListener(this)

  def process(e: KeyEvent) {
    if(!e.isConsumed || e.getComponent.equals(this)) return

    processKeyEvent(e)
  }

  def execute(msg: String) = {
    log.trace("executing: {}",msg)

    if(historyIndex != -1) {
      history.remove(historyIndex)
    }

    var i = 0
    while(i < history.size) {
      if(history(i) == msg) history.remove(i) else i += 1
    }


  }

  override def keyTyped(e: KeyEvent): Unit = {}

  override def keyPressed(e: KeyEvent): Unit = {
    e.getKeyCode match {
      case KeyEvent.VK_UP => historyUp
      case KeyEvent.VK_DOWN => historyDown
      case KeyEvent.VK_ENTER =>
      case _ => {
        e.consume()
        val text = getText
        selectAll
        execute(text)
      }
    }
  }

  def historyUp = ???
  def historyDown = ???

  override def keyReleased(e: KeyEvent): Unit = {}
}
