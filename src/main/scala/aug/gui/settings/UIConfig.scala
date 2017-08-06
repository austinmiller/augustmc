package aug.gui.settings

import java.awt.GridBagConstraints
import javax.swing.{BorderFactory, JLabel}

class CommandLineConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val fontButton = new FontChooserButton(profileConfigPanel)

  addToGrid(fontButton, 0, 0)
  fillHorizontal(3, 0)

  setTitledBorder("command line")
}

class WindowConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val fontButton = new FontChooserButton(profileConfigPanel)

  private val echoLabel = new JLabel("echo cmds")
  echoLabel.setToolTipText("If checked, commands will be shown in the console.")
  val echoCheck = new CheckBox(profileConfigPanel.setDirty())

  private val onNewLineLabel = new JLabel("separate cmd line")
  onNewLineLabel.setToolTipText("If checked, commands appear on a separate line.")
  val onNewLineCheck = new CheckBox(profileConfigPanel.setDirty())

  private val stackLabel = new JLabel("stack cmds")
  stackLabel.setToolTipText("If not checked, each command has its own line.")
  val stackCheck = new CheckBox(profileConfigPanel.setDirty())

  addToGrid(fontButton, 0, 0)

  c.insets = LeftInsets
  addToGrid(echoLabel, 1, 0)
  c.insets = NoInsets
  addToGrid(echoCheck, 2, 0)

  c.insets = LeftInsets
  addToGrid(onNewLineLabel, 3, 0)
  c.insets = NoInsets
  addToGrid(onNewLineCheck, 4, 0)

  c.insets = LeftInsets
  addToGrid(stackLabel, 5, 0)
  c.insets = NoInsets
  addToGrid(stackCheck, 6, 0)

  fillHorizontal(7, 0)

  setTitledBorder("console")
}

class LoggingConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  private val autoLogLabel = new JLabel("autolog")
  val autoLogCombo = new ComboBox(Array("none", "without color", "with color", "both"), profileConfigPanel.setDirty())

  c.insets = LeftInsets
  addToGrid(autoLogLabel, 0, 0)
  c.insets = NoInsets
  addToGrid(autoLogCombo, 1, 0)

  fillHorizontal(2, 0)

  setTitledBorder("logging")
}

class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {

  val commandLineConfigPanel = new CommandLineConfigPanel(profileConfigPanel)
  val consoleWindowConfigPanel = new WindowConfigPanel(profileConfigPanel)
  val loggingConfigPanel = new LoggingConfigPanel(profileConfigPanel)

  c.fill = GridBagConstraints.HORIZONTAL
  addToGrid(commandLineConfigPanel, 0, 0)
  addToGrid(consoleWindowConfigPanel, 0, 1)
  addToGrid(loggingConfigPanel, 0, 2)

  fillVertical(0, 3)

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
}
