package aug.io

import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

import aug.gui.SystemPanel
import aug.util.Util
import org.apache.commons.lang.exception.ExceptionUtils

class SystemLog(systemPanel: SystemPanel) {

  val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
  private var lineNum : Long = 0

  def raw(msg: String) = {
    msg.split("\n", -1).foreach { s =>
      systemPanel.text.setLine(lineNum, s)
      lineNum += 1
    }

    systemPanel.repaint()
  }

  def info(msg: String, args: Object*) = log("INFO", "37", msg, args)
  def error(msg: String) = log("ERROR", "31", msg)
  def error(msg: String, throwable: Throwable) = {
    log("ERROR", "31", s"$msg\n${ExceptionUtils.getStackTrace(throwable)}")
  }

  private def log(category: String, colorCode: String, msg: String, args: Object*) = {
    val m = String.format(msg, args)
    val txt = Util.colorCode(colorCode) + dateFormat.format(new Date) +
      " " + category + ": " + Util.colorCode("0") + msg
    raw(txt)
  }

}
