package aug.gui.settings

import java.awt.Font


class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val button = new FontChooserButton(profileConfigPanel, new Font("menlo", 0, 12))

  addToGrid(button, 0, 0)
}
