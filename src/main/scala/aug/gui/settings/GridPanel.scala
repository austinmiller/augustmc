package aug.gui.settings

import java.awt.{Component, GridBagConstraints, GridBagLayout}
import javax.swing.JPanel

class GridPanel extends JPanel {
  setLayout(new GridBagLayout)
  protected val c = new GridBagConstraints()

  def addToGrid(comp: Component, x: Int, y: Int, xw: Int = 1, xy: Int = 1, xl: Int = 1, yl: Int = 1) : Unit = {
    c.gridx = x
    c.gridy = y
    c.weightx = xw
    c.weighty = xy
    c.gridwidth = xl
    c.gridheight = yl
    add(comp, c)
  }

}
