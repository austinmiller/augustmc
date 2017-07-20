package aug.gui.settings

import java.awt.event.{ActionEvent, ActionListener, KeyEvent, KeyListener}
import java.awt._
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}

import aug.profile.{ProfileConfig, TelnetConfig}
import com.bulenkov.darcula.ui.DarculaTabbedPaneUI

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

class ClasspathPanel extends JPanel {
  val etchedBorder = BorderFactory.createEtchedBorder()
  setBorder(BorderFactory.createTitledBorder(etchedBorder, "classpath"))
  val springLayout = new SpringLayout
  setLayout(new GridBagLayout)

  val jlist = new JList[String]()
  jlist.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
  jlist.setLayoutOrientation(JList.VERTICAL)
  val listScroller = new JScrollPane(jlist)

  val addButton = new JButton("add")
  val deleteButton = new JButton("delete")

  val c = new GridBagConstraints()
  c.anchor = GridBagConstraints.WEST
  c.fill = GridBagConstraints.BOTH
  c.weightx = 100
  c.weighty = 100
  c.insets = new Insets(10, 10, 5, 10)
  c.gridwidth = 3
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
  c.insets = new Insets(0, 0, 0, 10)
  add(addButton, c)
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

}

class CpFileChooserDialog(settingsWindow: SettingsWindow) extends
  JDialog(settingsWindow, "Classpath Chooser", Dialog.ModalityType.DOCUMENT_MODAL) {
  
}

class JavaConfigPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel {

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

  val classpathPanel = new ClasspathPanel

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
  val javaConfigPanel = new JavaConfigPanel(this)
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
  }

  def setDirty() : Unit = settingsWindow.setProfileDirty(profileConfig.name)

  def constructProfileConfig = {
    val host = telnetConfigPanel.hostPanel.hostField.getText
    val port = Try {
      telnetConfigPanel.hostPanel.portField.getText.toInt
    } match {
      case Failure(e) => profileConfig.telnetConfig.port
      case Success(port) => port
    }

    ProfileConfig(
      name = profileConfig.name,
      telnetConfig = TelnetConfig(
        host = host,
        port = port
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