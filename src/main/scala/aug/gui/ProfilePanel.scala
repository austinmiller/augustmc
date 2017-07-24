package aug.gui

import java.awt.{Component, GridLayout}
import javax.swing.border.EmptyBorder
import javax.swing.{JPanel, SpringLayout}

import aug.profile.{Profile, ProfileConfig}

class ProfilePanel(val mainWindow: MainWindow, val profile: Profile) extends JPanel {
  private val springLayout = new SpringLayout
  private val container = new JPanel
  container.setLayout(new GridLayout(1, 1))
  val commandLine = new CommandLine(profile)

  commandLine.grabFocus

  setLayout(springLayout)
  add(container)
  add(commandLine)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  springLayout.putConstraint(SpringLayout.WEST, container, 0, SpringLayout.WEST, this)
  springLayout.putConstraint(SpringLayout.NORTH, container, 0, SpringLayout.NORTH, this)
  springLayout.putConstraint(SpringLayout.EAST, container, 0, SpringLayout.EAST, this)

  springLayout.putConstraint(SpringLayout.WEST, commandLine, 0, SpringLayout.WEST, this)
  springLayout.putConstraint(SpringLayout.SOUTH, commandLine, 0, SpringLayout.SOUTH, this)
  springLayout.putConstraint(SpringLayout.EAST, commandLine, 0, SpringLayout.EAST, this)

  springLayout.putConstraint(SpringLayout.SOUTH, container, 0, SpringLayout.NORTH, commandLine)

  def setContents(component: Component): Unit = {
    container.removeAll()
    container.add(component)
    repaint()
  }

  def setProfileConfig(profileConfig: ProfileConfig): Unit = {
    commandLine.setFont(profileConfig.commandLineFont.toFont)
    repaint()
  }
}
