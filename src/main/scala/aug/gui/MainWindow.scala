package aug.gui

import java.awt.event._
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

  val tabbedPane = new TabbedPane(this)
  val menus = new JMenuBar

  val settingsWindow = new SettingsWindow(this)

  add(tabbedPane)

  setTitle("August MC")
  setSize(1000,900)

  private val profileMenu: JMenu = new JMenu("profile")

  private val preferences = new JMenuItem("preferences")
  private val openProfileMenuItem = new JMenuItem("open profile")
  private val closeProfileMenuItem = new JMenuItem("close profile")

  private val connectionsMenu: JMenu = new JMenu("connections")

  private val connectMenuItem = new JMenuItem("connect")
  private val reconnectMenuItem = new JMenuItem("reconnect")
  private val disconnectMenuItem = new JMenuItem("disconnect")

  profileMenu.add(openProfileMenuItem)
  profileMenu.add(closeProfileMenuItem)

  connectionsMenu.add(connectMenuItem)
  connectionsMenu.add(reconnectMenuItem)
  connectionsMenu.add(disconnectMenuItem)

  menus.add(profileMenu)
  menus.add(connectionsMenu)

  openProfileMenuItem.setAccelerator(KeyStroke.getKeyStroke("meta O"))
  closeProfileMenuItem.setAccelerator(KeyStroke.getKeyStroke("meta W"))

  reconnectMenuItem.setAccelerator(KeyStroke.getKeyStroke("meta R"))
  connectMenuItem.setAccelerator(KeyStroke.getKeyStroke("meta C"))
  disconnectMenuItem.setAccelerator(KeyStroke.getKeyStroke("meta D"))

  if (OsTools.isMac) {
    OsTools.macHandlePreferences(() => displaySettings)
    OsTools.macHandleQuit(() => Main.exit)
  } else {
    profileMenu.add(preferences)

    preferences.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = displaySettings
    })
  }

  openProfileMenuItem.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = openProfile
  })

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

  connectMenuItem.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = tabbedPane.active.profile.connect
  })

  reconnectMenuItem.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = tabbedPane.active.profile.reconnect
  })

  disconnectMenuItem.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = tabbedPane.active.profile.disconnect
  })

  closeProfileMenuItem.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      ConfigManager.deactivateProfile(tabbedPane.active.profile.name)
    }
  })

  def openProfile = new OpenProfileDialog(this)

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


