package aug.gui

import java.awt.event.{KeyEvent, KeyListener, MouseWheelEvent, MouseWheelListener}

import aug.profile.Profiles


class GlobalMouseWheelListener extends MouseWheelListener {
  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    val notches = e.getWheelRotation
    e.consume
  }
}

case class HotKey(val char: Char, val ctrl: Boolean = false, val alt: Boolean = true)

class GlobalKeyListener extends KeyListener {

  import scala.collection.mutable

  val keysToIgnore = Set(HotKey('c',ctrl=true))

  val hotkeys : mutable.Map[HotKey,()=>Unit]= {
    mutable.Map(
      HotKey('1',alt=true)-> (()=>selectTab(0)),
      HotKey('2',alt=true)-> (()=>selectTab(1)),
      HotKey('3',alt=true)-> (()=>selectTab(2)),
      HotKey('4',alt=true)-> (()=>selectTab(3)),
      HotKey('5',alt=true)-> (()=>selectTab(4)),
      HotKey('6',alt=true)-> (()=>selectTab(5)),
      HotKey('7',alt=true)-> (()=>selectTab(6)),
      HotKey('8',alt=true)-> (()=>selectTab(7)),
      HotKey('9',alt=true)-> (()=>selectTab(8)),
      HotKey('1',ctrl=true)-> (()=>Profiles.active.startScript)
    )
  }

  def selectTab(tab: Int): Unit = {
    if(MainTabbedPane.getTabCount > tab) {
      MainTabbedPane.setSelectedIndex(tab)
    }
  }

  override def keyTyped(e: KeyEvent): Unit = {}

  override def keyPressed(e: KeyEvent): Unit = {
    val hk = HotKey(e.getKeyChar,alt=e.isAltDown,ctrl=e.isControlDown)

    if(keysToIgnore.contains(hk) || e.isConsumed) return

    hotkeys.get(hk) map { f : (()=>Unit) =>
      f()
      e.consume
    }
  }

  override def keyReleased(e: KeyEvent): Unit = {}
}