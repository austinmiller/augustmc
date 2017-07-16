package aug.gui

import java.awt._
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, JTabbedPane, SpringLayout, UIManager}

import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

class TabPanel extends JPanel {
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

class TabbedPane extends JTabbedPane {
  addTab("system", new TabPanel)

  setUI(new DarculaTabbedPaneUI() {
    override def paintFocusIndicator(g: Graphics, tabPlacement: Int, rects: Array[Rectangle], tabIndex: Int,
                                     iconRect: Rectangle, textRect: Rectangle, isSelected: Boolean): Unit = {}
  })

  def active = getComponentAt(getSelectedIndex).asInstanceOf[TabPanel]

}