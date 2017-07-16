package aug.gui

import java.awt.event.{WindowEvent, WindowListener}
import java.awt.{Frame, Insets}
import javax.imageio.ImageIO
import javax.swing._
import javax.swing.event.{MenuEvent, MenuListener}

import aug.gui.settings.SettingsWindow
import aug.io.TransparentColor
import aug.profile.ConfigManager
import com.bulenkov.darcula.DarculaLaf
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

class MainWindow extends JFrame {
  import MainWindow.log

  val tabbedPane = new TabbedPane
  val menus = new JMenuBar

  val settingsWindow = new SettingsWindow(this)

  add(tabbedPane)

  setTitle("August MC")
  setSize(1000,900)

  private val fileMenu: JMenu = new JMenu("File")
  private val preferences: JMenu = new JMenu("Preferences")

  menus.add(fileMenu)

  if (OsTools.isMac) {
    OsTools.macHandlePreferences(() => displaySettings)
    OsTools.macHandleQuit(() => Main.exit)
  } else {
    fileMenu.add(preferences)

    preferences.addMenuListener(new MenuListener {
      override def menuSelected(e: MenuEvent): Unit = {}

      override def menuDeselected(e: MenuEvent): Unit = displaySettings

      override def menuCanceled(e: MenuEvent): Unit = {}
    })
  }

  setJMenuBar(menus)

  val icon = ImageIO.read(MainWindow.getClass.getResourceAsStream("leaf.png"))
  setIconImage(icon)
  OsTools.setDockIcon(icon)

  addWindowListener(new WindowListener {
    override def windowDeiconified(e: WindowEvent): Unit = {}
    override def windowClosing(e: WindowEvent): Unit = Main.exit
    override def windowClosed(e: WindowEvent): Unit = {}
    override def windowActivated(e: WindowEvent): Unit = {}
    override def windowOpened(e: WindowEvent): Unit = {}
    override def windowDeactivated(e: WindowEvent): Unit = {}
    override def windowIconified(e: WindowEvent): Unit = {}
  })

  def displaySettings = {
    settingsWindow.setVisible(true)
    settingsWindow.toFront()
  }

}

object MainWindow {
  val log = Logger(LoggerFactory.getLogger(MainWindow.getClass))
}

object Main extends App {
  ConfigManager.load

  OsTools.init("August MC")

  UIManager.setLookAndFeel(new DarculaLaf)

  UIManager.put("Tree.textBackground", TransparentColor)
  UIManager.put("TabbedPane.contentBorderInsets", new Insets(6,0,0,0))
  UIManager.put("TabbedPane.tabInsets", new Insets(3,10,3,10))
  UIManager.put("TextArea.margin", 10)
  UIManager.put("Button.darcula.disabledText.shadow", TransparentColor)

  val mainWindow = new MainWindow
  mainWindow.setVisible(true)

  def colorCode(code: String) = "" + 27.toByte.toChar + "[" + code + "m"

  val text = mainWindow.tabbedPane.active.text

  text.addText("hello world\n" + colorCode("33;44") + "next liney" +
    colorCode("46;34") + " more of this line" +
    "\n" + colorCode("36;42") + "third line" + colorCode("0"))

  mainWindow.tabbedPane.active.textArea.repaint()

  def exit : Unit = {
    Frame.getFrames.foreach(_.dispose())
    System.exit(0)
  }

}


