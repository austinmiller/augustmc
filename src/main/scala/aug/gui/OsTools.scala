package aug.gui

import java.awt.{Image, Toolkit}

import com.apple.mrj.{MRJApplicationUtils, MRJPrefsHandler, MRJQuitHandler}

object OsTools {

  val isMac = System.getProperty("os.name") == "Mac OS X"
  val isLinux = System.getProperty("os.name") == "Linux"

  val shortcutKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()

  val accelerator = if(OsTools.isMac) "meta" else "control"

  def setDockIcon(icon: Image) : Unit = {
    if (isMac == false) return

    val appclass = Class.forName("com.apple.eawt.Application")

    val getApplication = appclass.getMethod("getApplication")
    val application = getApplication.invoke(null)

    val setDockIconImage = appclass.getMethod("setDockIconImage", classOf[Image])

    setDockIconImage.invoke(application, icon)
  }

  def init(name: String) : Unit = {
    if (isMac) {
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", name)
      System.setProperty("apple.laf.useScreenMenuBar", "true")
    }
  }

  def macHandlePreferences(callback: => Unit) = {
    MRJApplicationUtils.registerPrefsHandler(new MRJPrefsHandler {
      override def handlePrefs(): Unit = callback
    })
  }

  def macHandleQuit(callback: => Unit) = {
    MRJApplicationUtils.registerQuitHandler(new MRJQuitHandler {
      override def handleQuit(): Unit = callback
    })
  }
}
