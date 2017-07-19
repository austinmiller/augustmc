package aug.io

import java.text.SimpleDateFormat
import java.util.Date

import aug.gui.SystemPanel
import aug.util.Util

class SystemLog(systemPanel: SystemPanel) {

  val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
  private var lineNum : Long = 0

  def raw(msg: String) = {
    systemPanel.text.setLine(lineNum, msg)
    lineNum += 1
    systemPanel.repaint()
  }

  def info(msg: String, args: Object*) = log("INFO", "37", msg, args)
  def error(msg: String, args: Object*) = log("ERROR", "31", msg, args)

  private def log(category: String, colorCode: String, msg: String, args: Object*) = {
    val m = String.format(msg, args)
    val txt = Util.colorCode(colorCode) + dateFormat.format(new Date) +
      " " + category + ": " + Util.colorCode("0") + msg
    raw(txt)
  }

}
