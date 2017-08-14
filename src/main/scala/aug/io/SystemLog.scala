package aug.io

import java.text.SimpleDateFormat
import java.util.Date

import aug.gui.{SystemPanel, TabbedPane}
import aug.script.framework.tools.ScalaUtils
import org.apache.commons.lang.exception.ExceptionUtils

trait SystemLogInterface {
  def raw(msg: String): Unit
  def info(msg: String): Unit
  def error(msg: String): Unit
  def error(msg: String, throwable: Throwable): Unit
}

class PrefixSystemLog(prefix: String, systemLog: SystemLog) extends SystemLogInterface {
  override def info(msg: String): Unit = systemLog.info(s"$prefix$msg")
  override def error(msg: String): Unit = systemLog.error(s"$prefix$msg")
  override def error(msg: String, throwable: Throwable): Unit = systemLog.error(s"$prefix$msg", throwable)
  override def raw(msg: String): Unit = systemLog.raw(msg)
}

class SystemLog(systemPanel: SystemPanel, tabbedPane: TabbedPane) extends SystemLogInterface {
  val dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
  private var lineNum : Long = 0

  override def raw(msg: String): Unit = {
    msg.split("\n", -1).foreach { s =>
      systemPanel.text.setLine(lineNum, s)
      lineNum += 1
    }

    systemPanel.repaint()
  }

  override def info(msg: String): Unit = log("INFO", "37", msg)
  override def error(msg: String): Unit = {
    log("ERROR", "31", msg)
    tabbedPane.addError()
  }

  override def error(msg: String, throwable: Throwable): Unit = {
    log("ERROR", "31", s"$msg\n${ExceptionUtils.getStackTrace(throwable)}")
    tabbedPane.addError()
  }

  private def log(category: String, colorCode: String, msg: String): Unit = {
    val txt = ScalaUtils.encodeColor(colorCode) + dateFormat.format(new Date) +
      " " + category + ": " + ScalaUtils.encodeColor("0") + msg
    raw(txt)
  }
}
