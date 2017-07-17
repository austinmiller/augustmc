package aug.io

import java.text.SimpleDateFormat
import java.util.Date

import aug.gui.SystemPanel

class SystemLog(systemPanel: SystemPanel) {

  val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")

  def raw(msg: String) = {
    systemPanel.text.addText(msg)
  }

  def info(msg: String, args: Object*) = log("INFO", "37", msg, args)
  def error(msg: String, args: Object*) = log("ERROR", "31", msg, args)

  private def log(category: String, colorCode: String, msg: String, args: Object*) = {
    val m = String.format(msg, args)
    val txt = "\n" + ColorUtils.colorCode(colorCode) + dateFormat.format(new Date) +
      " " + category + ": " + ColorUtils.colorCode("0") + msg
    systemPanel.text.addText(txt)
    systemPanel.repaint()
  }

}
