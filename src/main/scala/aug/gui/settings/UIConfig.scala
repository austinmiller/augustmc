package aug.gui.settings

import java.awt.GridBagConstraints
import javax.swing.BorderFactory

class CommandLineConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val fontButton = new FontChooserButton(profileConfigPanel)

  addToGrid(fontButton, 0, 0)
  fillHorizontal(1, 0)

  setTitledBorder("command line")
}

class WindowConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val fontButton = new FontChooserButton(profileConfigPanel)

  addToGrid(fontButton, 0, 0)
  fillHorizontal(1, 0)

  setTitledBorder("window: console")
}

class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {

  val commandLineConfigPanel = new CommandLineConfigPanel(profileConfigPanel)
  val consoleWindowConfigPanel = new WindowConfigPanel(profileConfigPanel)

  c.fill = GridBagConstraints.HORIZONTAL
  addToGrid(commandLineConfigPanel, 0, 0)
  addToGrid(consoleWindowConfigPanel, 0, 1)

  fillVertical(0, 2)

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
}
