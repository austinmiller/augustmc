package aug.gui

import java.awt.event._
import java.awt.{Desktop, Frame, Insets}
import javax.imageio.ImageIO
import javax.swing._

import aug.gui.settings.SettingsWindow
import aug.io.{ConnectionManager, SystemLog, TransparentColor}
import aug.profile.{ConfigManager, Profile}
import aug.util.{LoremIpsum, Util}
import com.bulenkov.darcula.DarculaLaf
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Try}

class MainWindow extends JFrame {
  import MainWindow.log
  import Util.Implicits._

  val systemPanel = new SystemPanel(this)
  val slog = new SystemLog(systemPanel)
  slog.raw(Util.fullName)

  val tabbedPane = new TabbedPane(this)
  tabbedPane.addTab("system", systemPanel)

  val menus = new JMenuBar

  val settingsWindow = new SettingsWindow(this)

  add(tabbedPane)

  setTitle(Util.fullName)
  setSize(1000,900)

  // profile menu

  private val profileMenu: JMenu = new JMenu("profile")

  private val preferences = new JMenuItem("preferences")
  private val openProfileMenuItem = new JMenuItem("open profile")
  private val closeProfileMenuItem = new JMenuItem("close profile")
  private val openConfigDirMenuItem = new JMenuItem("open config dir")

  profileMenu.add(openProfileMenuItem)
  profileMenu.add(closeProfileMenuItem)
  profileMenu.add(new JSeparator)
  profileMenu.add(openConfigDirMenuItem)

  val acclr = OsTools.accelerator

  if (OsTools.isMac) {
    OsTools.macHandlePreferences(displaySettings())
    OsTools.macHandleQuit(Main.exit())
  } else {
    profileMenu.add(preferences)
    preferences.addActionListener(displaySettings())
    preferences.setAccelerator(KeyStroke.getKeyStroke(s"$acclr S"))
  }

  openProfileMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr O"))
  closeProfileMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr W"))

  openProfileMenuItem.addActionListener(openProfile())
  addProfileAction(closeProfileMenuItem, (profile: Profile) => ConfigManager.deactivateProfile(profile.name))
  openConfigDirMenuItem.addActionListener(openConfigDir())

  // connections menu

  private val connectionsMenu: JMenu = new JMenu("connections")

  private val connectMenuItem = new JMenuItem("connect")
  private val reconnectMenuItem = new JMenuItem("reconnect")
  private val disconnectMenuItem = new JMenuItem("disconnect")

  connectionsMenu.add(connectMenuItem)
  connectionsMenu.add(reconnectMenuItem)
  connectionsMenu.add(disconnectMenuItem)

  addProfileAction(connectMenuItem, (profile: Profile) => profile.connect())
  addProfileAction(reconnectMenuItem, (profile: Profile) => profile.reconnect())
  addProfileAction(disconnectMenuItem, (profile: Profile) => profile.disconnect())

  reconnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr R"))
  connectMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr T"))
  disconnectMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr D"))

  // client menu

  private val clientMenu: JMenu = new JMenu("client")

  private val clientStartMenuItem = new JMenuItem("start client")
  private val clientRestartMenuItem = new JMenuItem("restart client")
  private val clientStopMenuItem = new JMenuItem("client stop")

  clientStartMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"shift $acclr T"))
  clientRestartMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"shift $acclr R"))
  clientStopMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"shift $acclr D"))

  addProfileAction(clientStartMenuItem, (profile: Profile) => profile.clientStart())
  addProfileAction(clientRestartMenuItem, (profile: Profile) => profile.clientRestart())
  addProfileAction(clientStopMenuItem, (profile: Profile) => profile.clientStop())

  clientMenu.add(clientStartMenuItem)
  clientMenu.add(clientRestartMenuItem)
  clientMenu.add(clientStopMenuItem)

  // window menu

  private val windowMenu = new JMenu("window")
  private val selectTabMenu = new JMenu("select tab")
  private val copyTextMenuItem = new JMenuItem("copy text")

  for (i <- 0 to 9) {
    val selectTabMenuItem = new JMenuItem(s"select tab $i")
    selectTabMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr $i"))
    selectTabMenu.add(selectTabMenuItem)
    selectTabMenuItem.addActionListener(selectTab(i))
  }

  copyTextMenuItem.setAccelerator(KeyStroke.getKeyStroke(s"$acclr C"))

  copyTextMenuItem.addActionListener(tabbedPane.getSelectedComponent.asInstanceOf[HasHighlight].copyText())

  windowMenu.add(selectTabMenu)
  windowMenu.add(copyTextMenuItem)

  // add all main menus

  menus.add(profileMenu)
  menus.add(connectionsMenu)
  menus.add(clientMenu)
  menus.add(windowMenu)

  setJMenuBar(menus)

  Try {
    val icon = ImageIO.read(MainWindow.getClass.getResourceAsStream("leaf.png"))
    setIconImage(icon)
    OsTools.setDockIcon(icon)
  } match {
    case Failure(e) =>
      slog.error("failed to set icon")
      log.error(e.getMessage, e)
    case _ =>
  }

  addWindowListener(new WindowListener {
    override def windowDeiconified(e: WindowEvent): Unit = {}
    override def windowClosing(e: WindowEvent): Unit = Main.exit()
    override def windowClosed(e: WindowEvent): Unit = {}
    override def windowActivated(e: WindowEvent): Unit = {}
    override def windowOpened(e: WindowEvent): Unit = {}
    override def windowDeactivated(e: WindowEvent): Unit = {}
    override def windowIconified(e: WindowEvent): Unit = {}
  })

  private def addProfileAction(jMenuItem: JMenuItem, callback: (Profile) => Unit) : Unit = {
    jMenuItem.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        tabbedPane.active.map(_.profile).foreach(callback)
      }
    })
  }

  private def openProfile(): Unit = new OpenProfileDialog(this)

  private def displaySettings(): Unit = {
    settingsWindow.setVisible(true)
    settingsWindow.toFront()
  }

  private def openConfigDir(): Unit = {
    if (!Desktop.isDesktopSupported) {
      slog.error("desktop open is not supported")
      return
    }

    Desktop.getDesktop.open(ConfigManager.configDir)
  }

  private def selectTab(tabNum: Int): Unit = {
    if (tabbedPane.getTabCount > tabNum) {
      tabbedPane.setSelectedIndex(tabNum)
    }
  }

  setVisible(true)
}

object MainWindow {
  val log = Logger(LoggerFactory.getLogger(MainWindow.getClass))
}

object Main extends App {
  ConfigManager.load()
  ConnectionManager.start()

  OsTools.init("August MC")

  if(OsTools.isLinux) {
    // IconLoader has NPE without this.
    UIManager.getFont("Label.font")
  }

  UIManager.setLookAndFeel(new DarculaLaf)

  UIManager.put("Tree.textBackground", TransparentColor)
  UIManager.put("TabbedPane.contentBorderInsets", new Insets(6,0,0,0))
  UIManager.put("TabbedPane.tabInsets", new Insets(3,10,3,10))
  UIManager.put("TextArea.margin", 10)
  UIManager.put("Button.darcula.disabledText.shadow", TransparentColor)

  val mainWindow = new MainWindow

  if (Util.writeSharedJar(Util.sharedJarFile)) {
    mainWindow.slog.info(s"wrote shared.jar to ${Util.sharedJarFile}")
  }

  def exit(): Unit = {
    ConfigManager.closeAllProfiles
    ConnectionManager.close()
    Frame.getFrames.foreach(_.dispose())
    System.exit(0)
  }
}


