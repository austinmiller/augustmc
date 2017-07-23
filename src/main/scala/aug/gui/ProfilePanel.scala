package aug.gui

import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, SpringLayout}

import aug.profile.{Profile, ProfileConfig}

class ProfilePanel(val mainWindow: MainWindow, val profile: Profile) extends JPanel {
  val springLayout = new SpringLayout
  val text = new Text
  val textArea = new SplittableTextArea(text)
  val commandLine = new CommandLine(profile)

  commandLine.grabFocus

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


  def setLine(lineNum: Long, txt: String) = text.setLine(lineNum, txt)

  def addCommand(lineNum: Long, cmd: String) = text.addCommand(lineNum, cmd)

  def setProfileConfig(profileConfig: ProfileConfig): Unit = {
    commandLine.setFont(profileConfig.commandLineFont.toFont)
    textArea.setFont(profileConfig.consoleWindow.font.toFont)
    repaint()
  }
}
