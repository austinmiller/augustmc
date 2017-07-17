package aug.gui

import java.awt._
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JTabbedPane, SpringLayout, UIManager}

import aug.profile.Profile
import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

class ProfilePanel(val mainWindow: MainWindow, val profile: Profile) extends JPanel {
  val springLayout = new SpringLayout
  val text = new Text
  val textArea = new SplittableTextArea(text)
  val commandLine = new CommandLine

  commandLine.grabFocus

  textArea.setFont(new Font( "Monospaced", Font.PLAIN, 20 ))

  setLayout(springLayout)
  add(textArea)
  add(commandLine)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  springLayout.putConstraint(SpringLayout.WEST, textArea, 0, SpringLayout.WEST, this)
  springLayout.putConstraint(SpringLayout.NORTH, textArea, 0, SpringLayout.NORTH, this)
  springLayout.putConstraint(SpringLayout.EAST, textArea, 0, SpringLayout.EAST, this)

  springLayout.putConstraint(SpringLayout.WEST, commandLine, 0, SpringLayout.WEST, this)
  springLayout.putConstraint(SpringLayout.SOUTH, commandLine, 0, SpringLayout.SOUTH, this)
  springLayout.putConstraint(SpringLayout.EAST, commandLine, 0, SpringLayout.EAST, this)

  springLayout.putConstraint(SpringLayout.SOUTH, textArea, 0, SpringLayout.NORTH, commandLine)
}

class SystemPanel(mainWindow: MainWindow) extends JPanel {
  setLayout(new GridLayout(1, 1))

  val text = new Text
  val textArea = new SplittableTextArea(text)

  textArea.setFont(new Font( "Monospaced", Font.PLAIN, 14 ))

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