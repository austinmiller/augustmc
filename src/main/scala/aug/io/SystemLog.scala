package aug.io

import java.text.SimpleDateFormat
import java.util.Date

import aug.gui.SystemPanel

class SystemLog(systemPanel: SystemPanel) {

  val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")

  def raw(msg: String) = {
    systemPanel.text.addText(msg)
  }

  def info(msg: String, args: Object*) = {
    val m = String.format(msg, args)
    val txt = "\n" + ColorUtils.colorCode("37") +
      dateFormat.format(new Date) + " INFO: " + ColorUtils.colorCode("0") + msg
    systemPanel.text.addText(txt)
    systemPanel.repaint()
  }

}
