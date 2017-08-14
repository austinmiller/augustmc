package aug.gui.settings

import java.awt.GridBagConstraints
import javax.swing.{JLabel, JPanel}

class MongoConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {

  private val txtWidth = 90
  private val fieldWeight = 6

  val enabledBox = new EnabledBox(profileConfigPanel.setDirty())
  val userField = new RegexTextField("^.{1,60}", txtWidth, () => profileConfigPanel.setDirty())
  val passField = new PasswordField(txtWidth, profileConfigPanel.setDirty())
  val dbField = new RegexTextField("^.{1,60}", txtWidth, () => profileConfigPanel.setDirty())
  val hostField = new RegexTextField("^.{1,100}", txtWidth, () => profileConfigPanel.setDirty())

  c.fill = GridBagConstraints.HORIZONTAL
  margins(bot = 20)
  addToGrid(enabledBox, 0, 0, 1, 1, 2)

  margins()
  addToGrid(new JLabel("user"), 0, 1)
  addToGrid(userField, 1, 1, fieldWeight)
  margins(left = 20)
  addToGrid(new JLabel("password"), 2, 1)
  margins()
  addToGrid(passField, 3, 1, fieldWeight)

  addToGrid(new JLabel("database"), 0, 2)
  addToGrid(dbField, 1, 2, fieldWeight)
  margins(left = 20)
  addToGrid(new JLabel("host"), 2, 2)
  margins()
  addToGrid(hostField, 3, 2, fieldWeight)

  addToGrid(new JPanel(), 0, 3, 100, 100, 4)

  setMargin(10)
}