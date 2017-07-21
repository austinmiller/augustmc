package aug.gui.settings

import java.awt.event.{ActionEvent, ActionListener, KeyEvent, KeyListener}
import java.awt._
import java.io.{File, FilenameFilter}
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.filechooser.{FileFilter, FileSystemView}

import aug.gui.OsTools
import aug.profile.{JavaConfig, ProfileConfig, TelnetConfig}
import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class HostPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new FlowLayout())

  val border = BorderFactory.createTitledBorder(
    BorderFactory.createEtchedBorder(),
    "Host")

  setBorder(border)

  val hostField = new RegexTextField("^.{1,90}$", 20, profileConfigPanel.setDirty)
  val portField = new RegexTextField("^[1-9]{1}[0-9]{0,4}$", 5, profileConfigPanel.setDirty)

  add(new JLabel("host:"))
  add(hostField)
  add(new JLabel("port:"))
  add(portField)

  setMaximumSize(getPreferredSize())
}

class TelnetConfigPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {
  setLayout(new GridBagLayout)

  println(getComponentOrientation.isHorizontal)

  val c = new GridBagConstraints()

  c.anchor = GridBagConstraints.NORTH
  c.fill = GridBagConstraints.HORIZONTAL
  c.weightx = 1
  c.weighty = 1
  c.gridx = 0
  c.gridy = 0

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
  val hostPanel = new HostPanel(profileConfigPanel)

  add(hostPanel, c)
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

class UIConfigPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel

class ProfileConfigPanel(settingsWindow: SettingsWindow, var profileConfig: ProfileConfig) extends JPanel {
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
        port = toInt(telnetConfigPanel.hostPanel.portField.getText, profileConfig.telnetConfig.port)
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