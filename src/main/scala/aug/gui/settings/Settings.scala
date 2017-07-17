package aug.gui.settings

import java.awt._
import java.awt.event._
import javax.swing._
import javax.swing.event.{TreeSelectionEvent, TreeSelectionListener}
import javax.swing.tree.{DefaultMutableTreeNode, DefaultTreeModel, TreePath}

import aug.gui.MainWindow
import aug.io._
import aug.profile._

import scala.collection.mutable
import scala.util.{Failure, Try}

class SettingsNode(name: String, component: Component, panel: JPanel) extends DefaultMutableTreeNode(name) {
  def onSelection = {
    panel.removeAll()
    panel.add(component, BorderLayout.CENTER)
    panel.revalidate()
    panel.repaint()
  }
}

class SettingsTree private (settingsWindow: SettingsWindow, panel: JPanel) extends JTree {

  private var lastSelected = Array.empty[Object]

  val model = getModel.asInstanceOf[DefaultTreeModel]

  val root = new DefaultMutableTreeNode("root")
  val mainConfigNode = new SettingsNode("color schemes", settingsWindow.globalConfigPanel, panel)
  val profilesNode = new SettingsNode("profiles", settingsWindow.profilesConfigPanel, panel)

  root.add(mainConfigNode)
  root.add(profilesNode)

  setBackground(Color.decode("#3E434C"))
  setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BorderColor))
  setRootVisible(false)

  putClientProperty("JTree.lineStyle", "None")
  putClientProperty("TreeTableTree", true)

  model.setRoot(root)
  model.reload(root)

  setSelectionRow(0)

  def reload = {
    model.reload()
    for(i <- 1 to getRowCount) expandRow(i)
    Try {
      setSelectionPath(new TreePath(lastSelected))
    } match {
      case Failure(e) => setSelectionRow(0)
      case _ =>
    }
  }

  def addProfile(profileConfigPanel: ProfileConfigPanel) = {
    profilesNode.add(new SettingsNode(profileConfigPanel.name, profileConfigPanel, panel))
  }

  addTreeSelectionListener(new TreeSelectionListener {
    override def valueChanged(e: TreeSelectionEvent): Unit = {
      val tp = e.getNewLeadSelectionPath
      if (tp != null) {
        lastSelected = tp.getPath
        lastSelected.last.asInstanceOf[SettingsNode].onSelection
      }
    }
  })

  def setProfiles(profileConfigPanels: Seq[ProfileConfigPanel]): Unit = {
    profilesNode.removeAllChildren()
    profileConfigPanels.foreach(addProfile)
    setSelectionRow(0)
    reload
  }
}

object SettingsTree {

  val empty = new Icon {
    override def getIconHeight: Int = 0
    override def paintIcon(c: Component, g: Graphics, x: Int, y: Int): Unit = {}
    override def getIconWidth: Int = 0
  }

  def apply(settingsWindow: SettingsWindow, panel: JPanel) = {
    UIManager.put("Tree.closedIcon", empty)
    UIManager.put("Tree.openIcon", empty)
    UIManager.put("Tree.leafIcon", empty)
    UIManager.put("Tree.selectionBorderColor", new Color(0, 0, 0, 0))
    new SettingsTree(settingsWindow, panel)
  }
}

class OkPanel(settingsWindow: SettingsWindow) extends JPanel {
  setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BorderColor))

  val buttonPanel = new JPanel
  val flowLayout = new FlowLayout
  buttonPanel.setLayout(flowLayout)
  buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10))
  flowLayout.setHgap(0)

  val cancelButton = new JButton("cancel")
  buttonPanel.add(cancelButton)

  val applyButton = new JButton("apply")
  applyButton.setEnabled(false)
  buttonPanel.add(applyButton)
  val okButton = new JButton("OK")
  okButton.setSelected(true)
  buttonPanel.add(okButton)

  setLayout(new BorderLayout())
  add(buttonPanel, BorderLayout.EAST)


  cancelButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      settingsWindow.reloadFromConfig
      settingsWindow.setVisible(false)
    }
  })

  okButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      if (applyButton.isEnabled) {
        settingsWindow.saveSettings
      }

      settingsWindow.setVisible(false)
    }
  })

  applyButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      settingsWindow.saveSettings
    }
  })
}

class GlobalConfigPanel(settingsWindow: SettingsWindow) extends JPanel {
  add(new ColorSchemeConfigPanel(settingsWindow))
}

class SettingsWindow(val mainWindow: MainWindow) extends JDialog {

  import mainWindow.slog

  val colorSchemes = mutable.Map[String, ColorSchemeConfig]()
  val profiles = mutable.Map[String, ProfileConfigPanel]()

  var dirtyProfiles = mutable.Set[String]()
  var dirtyMainConfig = false

  val okPanel = new OkPanel(this)

  val globalConfigPanel = new GlobalConfigPanel(this)
  val profilesConfigPanel = new ProfilesConfigPanel(this)
  val rightPanel = new JPanel
  rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0))
  rightPanel.setLayout(new GridLayout(1,1))
  val tree = SettingsTree(this, rightPanel)

  reloadFromConfig

  val splitPane = new JSplitPane
  splitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT)
  splitPane.setDividerSize(0)
  splitPane.setDividerLocation(120)
  splitPane.setLeftComponent(tree)
  splitPane.setRightComponent(rightPanel)
  splitPane.setBorder(BorderFactory.createEmptyBorder())

  val vertSplitPane = new JSplitPane
  vertSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT)
  vertSplitPane.setDividerSize(0)
  vertSplitPane.setDividerLocation(335)
  vertSplitPane.setTopComponent(splitPane)
  vertSplitPane.setBottomComponent(okPanel)
  vertSplitPane.setBorder(BorderFactory.createEmptyBorder())

  getContentPane.add(vertSplitPane)

  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)

  setResizable(false)
  setTitle("Settings")
  setSize(647, 400)
  setLocationRelativeTo(mainWindow)

  def addProfile(name: String) = {
    profiles(name) = new ProfileConfigPanel(this, ProfileConfig(name))
    tree.addProfile(profiles(name))
    tree.reload
    setProfileDirty(name)
  }

  def reloadFromConfig = {
    ConfigManager.getMainConfig.colorSchemes.foreach { cs=> colorSchemes(cs.name) = cs }
    DefaultColorSchemes.colorSchemes.foreach { cs => colorSchemes(cs.name) = cs }

    profiles.clear
    ConfigManager.getProfiles.foreach { pc => profiles(pc.name) = new ProfileConfigPanel(this, pc) }
    tree.setProfiles(profiles.values.toList)

    okPanel.applyButton.setEnabled(false)
    dirtyMainConfig = false
    dirtyProfiles.clear
  }

  def setProfileDirty(name: String) = {
    dirtyProfiles.add(name)
    okPanel.applyButton.setEnabled(true)
  }

  def setMainConfigDirty = {
    dirtyMainConfig = true
    okPanel.applyButton.setEnabled(true)
  }

  def saveSettings : Unit = {
    if (dirtyMainConfig) {
      val mainConfig = {
        val defaultNames = DefaultColorSchemes.colorSchemes.map(_.name).toSet

        val newcs = colorSchemes.values.filter(cs => !defaultNames.contains(cs.name)).toArray

        MainConfig(newcs)
      }

      ConfigManager.setMainConfig(mainConfig)
    }

    dirtyProfiles.map(profiles(_)).foreach { pcp =>
      val pc = pcp.constructProfileConfig
      ConfigManager.setProfile(pc)
      profiles(pc.name).loadProfileConfig(pc)
    }

    dirtyMainConfig = true
    dirtyProfiles.clear
    okPanel.applyButton.setEnabled(false)

    slog.info("saved settings")
  }

  addWindowListener(new WindowListener {
    override def windowDeiconified(e: WindowEvent): Unit = {}
    override def windowClosing(e: WindowEvent): Unit = {}
    override def windowClosed(e: WindowEvent): Unit = {}
    override def windowActivated(e: WindowEvent): Unit = {}
    override def windowDeactivated(e: WindowEvent): Unit = {}
    override def windowIconified(e: WindowEvent): Unit = {}
    override def windowOpened(e: WindowEvent): Unit = {
      tree.setProfiles(profiles.values.toList)
    }
  })
}
