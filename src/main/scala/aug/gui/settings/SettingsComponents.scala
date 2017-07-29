package aug.gui.settings

import java.awt.event._
import java.awt._
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}

import aug.profile.FontConfig
import aug.util.Util

class RegexTextField(pattern: String, columns: Int, valueChangedCallback: () => Unit) extends JTextField(columns) {

  val textFieldBg = getBackground
  setMaximumSize(getPreferredSize)
  val errorBg = new Color(255, 85, 85)

  def isTextValid : Boolean = getText.matches(pattern)

  private def valueChanged = {
    valueChangedCallback()

    if (pattern.length == 0 || isTextValid) {
      setBackground(textFieldBg)
    } else {
      if (getText.trim.size > 0) {
        setBackground(errorBg)
      } else {
        setBackground(textFieldBg)
      }
    }
  }

  getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = valueChanged
    override def changedUpdate(e: DocumentEvent): Unit = valueChanged
    override def removeUpdate(e: DocumentEvent): Unit = valueChanged
  })
}

class PasswordField[T](columns: Int, valueChanged: => T) extends JPasswordField(columns) {
  setMaximumSize(getPreferredSize)
  getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = valueChanged
    override def changedUpdate(e: DocumentEvent): Unit = valueChanged
    override def removeUpdate(e: DocumentEvent): Unit = valueChanged
  })
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

class EnabledBox[T](onSelection: => T) extends JComboBox[String](Array("disabled", "enabled")) {
  def setSelectionEnabled(bool: String): Unit = setSelectionEnabled(bool.toBoolean)

  def setSelectionEnabled(bool: Boolean): Unit = {
    if (bool) setSelectedItem("enabled") else setSelectedItem("disabled")
  }

  def isSelectionEnabled = getSelectedItem == "enabled"

  addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = onSelection
  })
}

class FontChooserButton(profileConfigPanel: ProfileConfigPanel) extends JButton {
  var family = ""
  var fontSize = 12

  setText("placeholder")

  addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = fontChooser
  })

  def getSelectedFont = FontConfig(family, fontSize)

  def setSelectedFont(font: FontConfig): Unit = setSelectedFont(font.family, font.size)

  def setSelectedFont(family: String, fontSize: Int): Unit = {
    if (family != this.family || fontSize != this.fontSize) {
      if (this.family != "") profileConfigPanel.setDirty()
      this.family = family
      this.fontSize = fontSize
      setText(s"${family}, ${fontSize}")
      repaint()
    }
  }

  private def fontChooser : Unit = {
    val fc = new FontChooser(profileConfigPanel)
    if (family == "default") {
      fc.fontList.setSelectedIndex(0)
    } else fc.fontList.setSelectedValue(family, true)

    fc.sizeList.setSelectedValue(family, true)

    fc.addWindowListener(new WindowListener {
      override def windowDeiconified(e: WindowEvent): Unit = {}
      override def windowClosing(e: WindowEvent): Unit = {}
      override def windowClosed(e: WindowEvent): Unit = {
        val family = if (fc.fontList.getSelectedIndex == 0) {
          Util.defaultFont.getFamily
        } else fc.fontList.getSelectedValue
        val size = fc.sizeList.getSelectedValue

        setSelectedFont(family, size)
      }
      override def windowActivated(e: WindowEvent): Unit = {}
      override def windowOpened(e: WindowEvent): Unit = {}
      override def windowDeactivated(e: WindowEvent): Unit = {}
      override def windowIconified(e: WindowEvent): Unit = {}
    })

    fc.setVisible(true)
  }
}

class GridPanel extends JPanel {
  setLayout(new GridBagLayout)
  protected val c = new GridBagConstraints()

  def addToGrid(comp: Component, x: Int, y: Int, xw: Int = 1, xy: Int = 1, xl: Int = 1, yl: Int = 1) : Unit = {
    c.gridx = x
    c.gridy = y
    c.weightx = xw
    c.weighty = xy
    c.gridwidth = xl
    c.gridheight = yl
    add(comp, c)
  }

  def margins(top: Int = 0, left: Int = 0, bot: Int = 0, right: Int = 0): Unit = {
    c.insets = new Insets(top, left, bot, right)
  }

  def fillHorizontal(x: Int, y: Int): Unit = {
    addToGrid(new JPanel, x, y, 100)
  }

  def fillVertical(x: Int, y: Int): Unit = {
    addToGrid(new JPanel, x, y, 1, 100)
  }

  def setTitledBorder(title: String) : Unit = {
    setBorder(BorderFactory.createTitledBorder(
      BorderFactory.createEtchedBorder(),
      title))
  }

  def setMargin(margin: Int): Unit = {
    setBorder(BorderFactory.createEmptyBorder(margin, margin, margin, margin))
  }
}
