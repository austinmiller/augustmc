package aug.gui.settings

import java.awt.GridBagConstraints
import javax.swing.BorderFactory

class CommandLineConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val fontButton = new FontChooserButton(profileConfigPanel)

  addToGrid(fontButton, 0, 0)
  fillHorizontal(1, 0)

  setTitledBorder("command line")
}

class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {

  val commandLineConfigPanel = new CommandLineConfigPanel(profileConfigPanel)

  c.fill = GridBagConstraints.HORIZONTAL
  addToGrid(commandLineConfigPanel, 0, 0)

  fillVertical(0, 1)

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
}
