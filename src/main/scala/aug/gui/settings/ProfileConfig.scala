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
  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))

  setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
  val hostPanel = new HostPanel(profileConfigPanel)

  add(hostPanel)
}

class JavaConfigPanel(profileConfigPanel: ProfileConfigPanel) extends JPanel

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