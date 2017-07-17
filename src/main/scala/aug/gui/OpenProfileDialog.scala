package aug.gui

import java.awt.event.{ActionEvent, ActionListener}
import java.awt.{Dialog, Dimension, FlowLayout}
import javax.swing._

import aug.profile.ConfigManager

class OpenProfileDialog(mainWindow: MainWindow) extends
  JDialog(mainWindow, "Open Profile", Dialog.ModalityType.DOCUMENT_MODAL) {

  setSize(300, 180)
  setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
  setLocationRelativeTo(mainWindow)

  setLayout(new BoxLayout(getContentPane, BoxLayout.Y_AXIS))

  private val profiles: Array[String] = ConfigManager.getUnactivedProfiles.toArray
  val comboBox = new JComboBox[String](profiles)
  val cancelButton = new JButton("cancel")
  val openButton = new JButton("open")

  val flowPanel = new JPanel()
  flowPanel.setLayout(new FlowLayout())
  flowPanel.add(cancelButton)
  flowPanel.add(openButton)

  if(profiles.isEmpty) openButton.setEnabled(false)

  getRootPane.setDefaultButton(openButton)
  getRootPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

  comboBox.setMaximumSize(comboBox.getPreferredSize)

  add(comboBox)
  add(Box.createRigidArea(new Dimension(0, 10)))
  add(flowPanel)

  pack()

  cancelButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = setVisible(false)
  })

  openButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      ConfigManager.activateProfile(comboBox.getSelectedItem.toString, mainWindow)
      setVisible(false)
    }
  })

  setVisible(true)
}
