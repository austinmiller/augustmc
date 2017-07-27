package aug.gui

import java.awt._
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JTabbedPane}

import aug.util.Util
import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

class SystemPanel(mainWindow: MainWindow) extends JPanel with HasHighlight {
  setLayout(new GridLayout(1, 1))

  val textArea = new SplittableTextArea(this)
  val text = textArea.text

  textArea.setActiveFont(Util.defaultFont)

  add(textArea)

  setBorder(new EmptyBorder(0, 0, 0, 0))
}

class TabbedPane(mainWindow: MainWindow) extends JTabbedPane {

  setUI(new DarculaTabbedPaneUI() {
    override def paintFocusIndicator(g: Graphics, tabPlacement: Int, rects: Array[Rectangle], tabIndex: Int,
                                     iconRect: Rectangle, textRect: Rectangle, isSelected: Boolean): Unit = {}
  })

  def active : Option[ProfilePanel] = {
    val c = getComponentAt(getSelectedIndex)
    if (c.isInstanceOf[ProfilePanel]) Some(c.asInstanceOf[ProfilePanel]) else None
  }

  def addProfile(name: String, profilePanel: ProfilePanel) = {
    addTab(name, profilePanel)
    setSelectedComponent(profilePanel)
  }
}