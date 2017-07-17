package aug.gui

import java.awt.Font
import javax.swing.{JPanel, SpringLayout}
import javax.swing.border.EmptyBorder

import aug.profile.Profile

class ProfilePanel(val mainWindow: MainWindow, val profile: Profile) extends JPanel {
  val springLayout = new SpringLayout
  val text = new Text
  val textArea = new SplittableTextArea(text)
  val commandLine = new CommandLine(profile)

  commandLine.grabFocus

  textArea.setFont(new Font( "Monospaced", Font.PLAIN, 12 ))

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

  def addText(newText: String): Unit = {
    text.addText(newText)
    repaint()
  }
}
