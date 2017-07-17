package aug.io

import aug.gui.SystemPanel

class SystemLog(systemPanel: SystemPanel) {

  def raw(msg: String) = {
    systemPanel.text.addText(msg)
  }

  def info(msg: String, args: Object*) = {
    val m = String.format(msg, args)
    val txt = "\n" + ColorUtils.colorCode("37") + "INFO: " + ColorUtils.colorCode("0") + msg
    systemPanel.text.addText(txt)
    systemPanel.repaint()
  }

}
