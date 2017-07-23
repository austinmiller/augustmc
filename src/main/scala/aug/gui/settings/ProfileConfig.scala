package aug.gui.settings

import java.awt._
import java.awt.event._
import java.io.{File, FilenameFilter}
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.filechooser.{FileFilter, FileSystemView}

import aug.gui.OsTools
import aug.profile.{JavaConfig, ProfileConfig, TelnetConfig}
import aug.util.Util
import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}
import aug.util.Util.Implicits._

class HostPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new GridBagLayout())
  val c = new GridBagConstraints()

  val border = BorderFactory.createTitledBorder(
    BorderFactory.createEtchedBorder(),
    "Host")

  setBorder(border)

  val hostField = new RegexTextField("^.{1,90}$", 20, profileConfigPanel.setDirty)
  val portField = new RegexTextField("^[1-9]{1}[0-9]{0,4}$", 5, profileConfigPanel.setDirty)

  c.weightx = 1
  c.gridx = 0
  c.insets = new Insets(0, 10, 0, 0)
  c.gridy = 0
  add(new JLabel("host: "), c)

  c.fill = GridBagConstraints.HORIZONTAL
  c.weightx = 100
  c.insets = new Insets(0, 0, 0, 0)
  c.gridx = 1
  add(hostField, c)

  c.weightx = 1
  c.insets = new Insets(0, 10, 0, 0)
  c.gridx = 2
  add(new JLabel("port: "), c)

  c.weightx = 1
  c.gridx = 3
  c.insets = new Insets(0, 0, 0, 10)
  add(portField, c)

  setMaximumSize(getPreferredSize())
}

class EnabledBox extends JComboBox[String](Array("disabled", "enabled")) {
  def setSelectionEnabled(bool: Boolean) = {
    if (bool) setSelectedItem("enabled") else setSelectedItem("disabled")
  }

  def isSelectionEnabled = getSelectedItem == "enabled"
}

class GmcpPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new GridBagLayout)
  private val c = new GridBagConstraints()

  val enabledBox = new EnabledBox
  val supportLabel = new JLabel("core.supports.set: ")
  val supportsField = new RegexTextField("", 15, profileConfigPanel.setDirty)
  supportsField.setToolTipText("If non-empty, will send contents as GMCP option 'core.supports.set'.  Example " +
    "contents: \"one\", \"two\"")

  c.fill = GridBagConstraints.HORIZONTAL
  c.weightx = 1
  c.gridx = 0
  c.gridy = 0
  c.insets = new Insets(0, 10, 0, 0)
  add(enabledBox, c)

  c.gridx = 1
  c.insets = new Insets(0, 10, 0, 0)
  add(supportLabel, c)

  c.weightx = 100
  c.gridx = 2
  c.insets = new Insets(0, 0, 0, 10)
  add(supportsField, c)

  setBorder(BorderFactory.createTitledBorder(
    BorderFactory.createEtchedBorder(),
    "GMCP"))
}

class TelnetConfigPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new GridBagLayout)

  val c = new GridBagConstraints()

  c.anchor = GridBagConstraints.NORTH
  c.fill = GridBagConstraints.HORIZONTAL
  c.weightx = 1
  c.weighty = 1
  c.gridx = 0
  c.gridy = 0

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
  val hostPanel = new HostPanel(profileConfigPanel)
  val gmcpPanel = new GmcpPanel(profileConfigPanel)

  add(hostPanel, c)

  c.gridy = 1
  add(gmcpPanel, c)

  c.fill = GridBagConstraints.BOTH
  c.weighty = 100
  c.gridy= 2
  add(new JPanel(), c)
}

class ClasspathPanel(settingsWindow: SettingsWindow, profileConfigPanel: ProfileConfigPanel) extends JPanel {
  val etchedBorder = BorderFactory.createEtchedBorder()
  setBorder(BorderFactory.createTitledBorder(etchedBorder, "classpath"))
  val springLayout = new SpringLayout
  setLayout(new GridBagLayout)

  val jlist = new JList[String]()
  val model = new DefaultListModel[String]()
  jlist.setModel(model)
  jlist.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
  jlist.setLayoutOrientation(JList.VERTICAL)
  val listScroller = new JScrollPane(jlist)

  val addDirButton = new JButton("add directory")
  val addJarButton = new JButton("add jar")
  val deleteButton = new JButton("delete")

  val c = new GridBagConstraints()
  c.anchor = GridBagConstraints.WEST
  c.fill = GridBagConstraints.BOTH
  c.weightx = 100
  c.weighty = 100
  c.insets = new Insets(10, 10, 5, 10)
  c.gridwidth = 4
  c.ipadx = 20
  c.gridx = 0
  c.gridy = 0
  c.weightx = 80

  add(listScroller, c)

  c.anchor = GridBagConstraints.EAST
  c.fill = GridBagConstraints.NONE
  c.gridwidth = 1
  c.weightx = 100
  c.weighty = 1
  c.insets = new Insets(0, 0, 0, 10)
  c.gridy = 1

  c.gridx = 0
  add(new JPanel, c)

  c.weightx = 1
  c.gridx = 1
  c.insets = new Insets(0, 0, 0, 5)
  add(deleteButton, c)

  c.gridx = 2
  c.insets = new Insets(0, 0, 0, 5)
  add(addJarButton, c)


  c.gridx = 3
  c.insets = new Insets(0, 0, 0, 10)
  add(addDirButton, c)

  private def addFile(file: File) = {
    val path = file.getAbsolutePath
    if(!model.contains(path)) {
      model.addElement(path)
      profileConfigPanel.setDirty()
    }
  }

  deleteButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      jlist.getSelectedValuesList.toArray.foreach{s => model.removeElement(s)}
      profileConfigPanel.setDirty()
    }
  })

  addDirButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      if (OsTools.isMac) {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        val fd = new FileDialog(settingsWindow, "Choose a file", FileDialog.LOAD)
        fd.setMultipleMode(true)
        fd.setVisible(true)
        addFile(new File(fd.getDirectory, fd.getFile))
        System.setProperty("apple.awt.fileDialogForDirectories", "false")
      } else {
        val jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory())
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
        if(jfc.showOpenDialog(settingsWindow) == JFileChooser.APPROVE_OPTION) {
          addFile(jfc.getSelectedFile)
        }
      }
    }
  })

  addJarButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      if (OsTools.isMac) {
        val fd = new FileDialog(settingsWindow, "Choose a file", FileDialog.LOAD)
        fd.setMultipleMode(true)
        fd.setFilenameFilter(new FilenameFilter {
          override def accept(dir: File, name: String): Boolean = name.endsWith(".jar")
        })
        fd.setVisible(true)
        addFile(new File(fd.getDirectory, fd.getFile))
      } else {
        val jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory())
        jfc.setFileFilter(new FileFilter {
          override def accept(pathname: File): Boolean = pathname.getAbsolutePath.endsWith(".jar")
          override def getDescription: String = "jar files"
        })
        if(jfc.showOpenDialog(settingsWindow) == JFileChooser.APPROVE_OPTION) {
          addFile(jfc.getSelectedFile)
        }
      }
    }
  })
}

class JavaOptionsPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new GridBagLayout())
  val modeComboBox = new JComboBox[String](Array("disabled", "enabled", "autostart"))
  val timeoutLabel = new JLabel("timeout: ")
  val timeoutText = new RegexTextField("^[1-9]{1}[0-9]{0,4}$", 5, profileConfigPanel.setDirty)

  val c = new GridBagConstraints()
  c.anchor = GridBagConstraints.WEST
  c.weightx = 1
  c.gridx = 0
  c.gridy = 0
  add(modeComboBox, c)

  c.gridx = 1
  c.fill = GridBagConstraints.HORIZONTAL
  c.weightx = 100
  add(new JPanel, c)

  c.gridx = 2
  c.weightx = 1
  c.fill = GridBagConstraints.NONE
  add(timeoutLabel, c)
  c.gridx = 3
  add(timeoutText, c)

  modeComboBox.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      profileConfigPanel.setDirty()
    }
  })
}

class JavaConfigPanel(settingsWindow: SettingsWindow, profileConfigPanel: ProfileConfigPanel) extends JPanel {

  setLayout(new GridBagLayout)
  val c = new GridBagConstraints()

  val toprow = new JavaOptionsPanel(profileConfigPanel)

  val mainClassPanel = new JPanel()
  val etchedBorder = BorderFactory.createEtchedBorder()
  mainClassPanel.setBorder(BorderFactory.createTitledBorder(etchedBorder, "main class"))
  mainClassPanel.setLayout(new GridBagLayout)
  c.fill = GridBagConstraints.HORIZONTAL
  c.insets = new Insets(0, 10, 0, 10)
  c.weightx = 100
  val mainClassField = new RegexTextField("^.{1,300}$", 30, profileConfigPanel.setDirty)
  mainClassPanel.add(mainClassField, c)

  val classpathPanel = new ClasspathPanel(settingsWindow, profileConfigPanel)

  c.anchor = GridBagConstraints.NORTH
  c.insets = new Insets(0, 0, 0, 0)
  c.weightx = 1
  c.weighty = 1
  c.gridx = 0
  c.gridy = 0

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

  add(toprow, c)

  c.gridy = 1
  add(mainClassPanel, c)

  c.fill = GridBagConstraints.BOTH
  c.gridy = 2
  c.weighty = 400
  add(classpathPanel, c)
}

class ProfileDialog(profileConfigPanel: ProfileConfigPanel, title: String) extends
  JDialog(profileConfigPanel.settingsWindow, title, Dialog.ModalityType.DOCUMENT_MODAL) {

  getContentPane.setLayout(new GridBagLayout)
  protected val c = new GridBagConstraints()

  def addToGrid(comp: Component, x: Int, y: Int, xw: Int = 1, xy: Int = 1, xl: Int = 1, yl: Int = 1) = {
    c.gridx = x
    c.gridy = y
    c.weightx = xw
    c.weighty = xy
    c.gridwidth = xl
    c.gridheight = yl
    getContentPane.add(comp, c)
  }

  setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
  setLocationRelativeTo(profileConfigPanel.settingsWindow)
}

class FontChooser(profileConfigPanel: ProfileConfigPanel) extends ProfileDialog(profileConfigPanel, "choose font") {
  val fontList = new JList[String]()
  private val fontModel = new DefaultListModel[String]()
  private val fontScroller = new JScrollPane(fontList)
  val sizeList = new JList[Int]()
  private val sizeModel = new DefaultListModel[Int]()
  private val sizeScroller = new JScrollPane(sizeList)

  fontList.setModel(fontModel)
  fontList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  fontList.setLayoutOrientation(JList.VERTICAL)
  fontModel.addElement(s"default [${Util.defaultFont.getFamily}]")
  Util.monospaceFamilies.foreach(fontModel.addElement)

  sizeList.setModel(sizeModel)
  sizeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  sizeList.setLayoutOrientation(JList.VERTICAL)
  Util.fontSizes.foreach(sizeModel.addElement)

  // this abortion brought to you by Java
  getContentPane.asInstanceOf[JPanel].setBorder(BorderFactory.createEmptyBorder())
  c.fill = GridBagConstraints.BOTH
  addToGrid(fontScroller, 0, 0)
  addToGrid(sizeScroller, 1, 0)

  setSize(327, 200)

  private val kl = new KeyListener {
    override def keyTyped(e: KeyEvent): Unit = {}
    override def keyPressed(e: KeyEvent): Unit = {}
    override def keyReleased(e: KeyEvent): Unit = {
      if (e.getKeyCode == KeyEvent.VK_ENTER) {
        dispose()
      }
    }
  }

  fontList.addKeyListener(kl)
  sizeList.addKeyListener(kl)
  addKeyListener(kl)
}

class FontChooserButton(profileConfigPanel: ProfileConfigPanel, var selectedFont: Font) extends JButton {

  setSelectedFont(selectedFont)

  addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = fontChooser
  })

  private def setSelectedFont(font: Font): Unit = {
    selectedFont = font
    setText(s"${selectedFont.getFamily}, ${selectedFont.getSize}")
    repaint()
  }

  private def fontChooser : Unit = {
    val fc = new FontChooser(profileConfigPanel)
    fc.fontList.setSelectedValue(selectedFont.getFamily, true)
    fc.sizeList.setSelectedValue(selectedFont.getSize, true)

    fc.addWindowListener(new WindowListener {
      override def windowDeiconified(e: WindowEvent): Unit = {}
      override def windowClosing(e: WindowEvent): Unit = {}
      override def windowClosed(e: WindowEvent): Unit = {
        val family = if (fc.fontList.getSelectedIndex == 0) {
          Util.defaultFont.getFamily
        } else fc.fontList.getSelectedValue
        val size = fc.sizeList.getSelectedValue
        setSelectedFont(new Font(family, 0, size))
      }
      override def windowActivated(e: WindowEvent): Unit = {}
      override def windowOpened(e: WindowEvent): Unit = {}
      override def windowDeactivated(e: WindowEvent): Unit = {}
      override def windowIconified(e: WindowEvent): Unit = {}
    })

    fc.setVisible(true)
  }
}

class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends GridPanel {
  val button = new FontChooserButton(profileConfigPanel, new Font("menlo", 0, 12))

  addToGrid(button, 0, 0)
}

class ProfileConfigPanel(val settingsWindow: SettingsWindow, var profileConfig: ProfileConfig) extends JPanel {
  val name = profileConfig.name

  val telnetConfigPanel = new TelnetConfigPanel(this)
  val javaConfigPanel = new JavaConfigPanel(settingsWindow, this)
  val uiConfigPanel = new UIConfigPanel(this)

  val tabs = new JTabbedPane()
  tabs.addTab("telnet", telnetConfigPanel)
  tabs.addTab("java", javaConfigPanel)
  tabs.addTab("ui", uiConfigPanel)

  tabs.setUI(new DarculaTabbedPaneUI() {
    override def paintFocusIndicator(g: Graphics, tabPlacement: Int, rects: Array[Rectangle], tabIndex: Int,
                                     iconRect: Rectangle, textRect: Rectangle, isSelected: Boolean): Unit = {}
  })

  setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0))
  setLayout(new GridLayout(1, 1))

  add(tabs)

  loadProfileConfig(profileConfig)

  def loadProfileConfig(profileConfig: ProfileConfig) = {
    this.profileConfig = profileConfig

    telnetConfigPanel.hostPanel.hostField.setText(profileConfig.telnetConfig.host)
    telnetConfigPanel.hostPanel.portField.setText(profileConfig.telnetConfig.port.toString)
    telnetConfigPanel.gmcpPanel.enabledBox.setSelectionEnabled(profileConfig.telnetConfig.gmcpEnabled)
    telnetConfigPanel.gmcpPanel.supportsField.setText(profileConfig.telnetConfig.gmcpSupports)

    javaConfigPanel.toprow.timeoutText.setText(profileConfig.javaConfig.clientTimeout.toString)
    javaConfigPanel.toprow.modeComboBox.setSelectedItem(profileConfig.javaConfig.clientMode)
    javaConfigPanel.mainClassField.setText(profileConfig.javaConfig.mainClass)
    javaConfigPanel.classpathPanel.model.removeAllElements()
    profileConfig.javaConfig.classPath.foreach{javaConfigPanel.classpathPanel.model.addElement}
  }

  def setDirty() : Unit = settingsWindow.setProfileDirty(profileConfig.name)

  def constructProfileConfig = {
    def toInt(string: String, orig: Int) = {
      Try {
        string.toInt
      } match {
        case Failure(e) => orig
        case Success(port) => port
      }
    }

    def enumToArray[A:ClassTag](enumeration: java.util.Enumeration[A]) = {
      val arr = Array.newBuilder[A]
      while(enumeration.hasMoreElements) arr += enumeration.nextElement()
      arr.result()
    }

    ProfileConfig(
      name = profileConfig.name,
      telnetConfig = TelnetConfig(
        host = telnetConfigPanel.hostPanel.hostField.getText,
        port = toInt(telnetConfigPanel.hostPanel.portField.getText, profileConfig.telnetConfig.port),
        gmcpEnabled = telnetConfigPanel.gmcpPanel.enabledBox.isSelectionEnabled,
        gmcpSupports = telnetConfigPanel.gmcpPanel.supportsField.getText
      ),
      javaConfig = JavaConfig(
        clientMode = javaConfigPanel.toprow.modeComboBox.getSelectedItem.toString,
        mainClass = javaConfigPanel.mainClassField.getText,
        clientTimeout = toInt(javaConfigPanel.toprow.timeoutText.getText, profileConfig.javaConfig.clientTimeout),
        classPath = enumToArray(javaConfigPanel.classpathPanel.model.elements())
      )
    )
  }
}

class ProfilesConfigPanel(settingsWindow: SettingsWindow) extends JPanel {
  setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50))

  val lbl = new JLabel("new profile name:")
  val button = new JButton("create profile")
  val textField = new JTextField(16)
  val textFieldBg = textField.getBackground
  textField.setMaximumSize(textField.getPreferredSize)
  val errorBg = new Color(255, 85, 85)

  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))

  lbl.setAlignmentX(Component.CENTER_ALIGNMENT)
  add(lbl)
  textField.setAlignmentX(Component.CENTER_ALIGNMENT)
  add(textField)
  add(Box.createRigidArea(new Dimension(0, 20)))
  button.setAlignmentX(Component.CENTER_ALIGNMENT)
  add(button)

  clear

  private def clear = {
    textField.setText("")
    button.setEnabled(false)
  }

  private def isTextValid : Boolean = {
    val txt = textField.getText.trim
    txt.matches("^[a-zA-Z0-9 \\-_]{1,16}$") && txt != "system" && !settingsWindow.profiles.contains(txt)
  }

  private def valueChanged = {
    if (isTextValid) {
      textField.setBackground(textFieldBg)
      button.setEnabled(true)
    } else {
      button.setEnabled(false)

      if (textField.getText.trim.size > 0) {
        textField.setBackground(errorBg)
      } else {
        textField.setBackground(textFieldBg)
      }
    }
  }

  private def submit = {
    val newname = textField.getText.trim
    clear
    settingsWindow.addProfile(newname)
  }

  textField.getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = valueChanged
    override def changedUpdate(e: DocumentEvent): Unit = valueChanged
    override def removeUpdate(e: DocumentEvent): Unit = valueChanged
  })

  textField.addKeyListener(new KeyListener {
    override def keyTyped(e: KeyEvent): Unit = {}
    override def keyPressed(e: KeyEvent): Unit = {}
    override def keyReleased(e: KeyEvent): Unit = {
      if (e.getKeyCode() == KeyEvent.VK_ENTER && button.isEnabled) {
        submit
      }
    }
  })

  button.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      submit
    }
  })

}