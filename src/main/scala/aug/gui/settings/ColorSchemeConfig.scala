package aug.gui.settings

import java.awt._
import java.awt.event._
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import javax.swing._
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.plaf.ButtonUI

import aug.io.{ColorUtils, ConfigurableColorScheme, DefaultColorScheme, TelnetColorBlack, TelnetColorBlue, TelnetColorCyan, TelnetColorDefaultBg, TelnetColorDefaultFg, TelnetColorGreen, TelnetColorMagenta, TelnetColorRed, TelnetColorWhite, TelnetColorYellow}
import aug.profile.ColorSchemeConfig

import scala.collection.mutable

class ColorButton(val label: String, var color: Color, cscp: ColorSchemeConfigPanel) extends JButton {
  import ColorButton._

  setFont(font)

  setUI(new ButtonUI {})

  addMouseListener(new MouseListener {
    override def mouseExited(e: MouseEvent): Unit = {}
    override def mouseClicked(e: MouseEvent): Unit = {}
    override def mouseReleased(e: MouseEvent): Unit = {}
    override def mouseEntered(e: MouseEvent): Unit = {}

    override def mousePressed(e: MouseEvent): Unit = {
      val cbd = new JDialog(cscp.settingsWindow, "color chooser: " + label, Dialog.ModalityType.DOCUMENT_MODAL)
      cbd.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
      cbd.setLocationRelativeTo(cscp.settingsWindow)

      val chooser = new JColorChooser()
      chooser.setColor(color)
      cbd.add(chooser)
      cbd.pack()

      cbd.addWindowListener(new WindowListener {
        override def windowDeiconified(e: WindowEvent): Unit = {}
        override def windowClosed(e: WindowEvent): Unit = {}
        override def windowActivated(e: WindowEvent): Unit = {}
        override def windowOpened(e: WindowEvent): Unit = {}
        override def windowDeactivated(e: WindowEvent): Unit = {}
        override def windowIconified(e: WindowEvent): Unit = {}
        override def windowClosing(e: WindowEvent): Unit = {
          color = chooser.getColor
          repaint()
          cscp.setColor(label, color)
        }
      })

      cbd.setVisible(true)
    }
  })

  override def getPreferredSize: Dimension = new Dimension(90, 30)

  override def paintComponent(g: Graphics): Unit = {
    val g2d = g.asInstanceOf[Graphics2D]

    g.setColor(color)
    g2d.fill(new RoundRectangle2D.Float(3, 3, getWidth - 6, getHeight - 6, 5, 5))

    g.setColor(getTextColor)

    val x = (getWidth - fontWidth) / 2
    val y = fontHeight + ((getHeight - fontHeight) / 2) - fontDescent

    g.drawString(ColorUtils.toHex(color), x, y)
  }

  private def getTextColor = {
    val wcr = contrastRatio(Color.WHITE, color)
    val bcr = contrastRatio(color, Color.BLACK)
    if (wcr > bcr) Color.WHITE else Color.BLACK
  }

  private def contrastRatio(color1: Color, color2: Color) = {
    (relativeLuminance(color1) + 0.05) / (relativeLuminance(color2) + 0.05)
  }

  // https://www.w3.org/TR/2008/REC-WCAG20-20081211/#relativeluminancedef
  private def relativeLuminance(color: Color) = 0.2126*color.getRed + 0.7152*color.getGreen + 0.0722*color.getBlue
}

object ColorButton {
  val (font, fontHeight, fontDescent, fontWidth) = {
    val font = new Font("Monospaced", Font.BOLD, 12)
    val bf = new BufferedImage(200,80,BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val fm = bfg.getFontMetrics(font)

    (font, fm.getHeight, fm.getDescent, fm.stringWidth("#000000"))
  }
}


class NewColorSchemeDialog(settingsWindow: SettingsWindow, csgp: ColorSchemeConfigPanel) extends
  JDialog(settingsWindow, "New Color Scheme", Dialog.ModalityType.DOCUMENT_MODAL) {

  setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE)
  setLocationRelativeTo(settingsWindow)

  setSize(300, 180)
  setResizable(false)

  val panel = new JPanel
  panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS))

  val textField = new JTextField(10)
  textField.setMaximumSize(new Dimension(200, 40))
  val textFieldBg = textField.getBackground
  val errorBg = new Color(255, 85, 85)

  val enterButton = new JButton("create")
  val cancelButton = new JButton("cancel")

  val msg = "What name for the color scheme? (letters and spaces only)"
  val label = new JTextArea(msg)
  label.setEditable(false)
  label.setOpaque(false)
  label.setEnabled(false)
  label.setMaximumSize(new Dimension(300, 400))
  label.setLineWrap(true)
  label.setWrapStyleWord(true)

  val flowPanel = new JPanel
  flowPanel.setLayout(new FlowLayout())

  flowPanel.add(cancelButton)
  flowPanel.add(enterButton)

  panel.add(label)
  panel.add(Box.createRigidArea(new Dimension(0,20)))
  panel.add(textField)
  panel.add(Box.createRigidArea(new Dimension(0,20)))
  panel.add(flowPanel)

  panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

  add(panel)

  getRootPane.setDefaultButton(enterButton)

  def clear = {
    textField.setText("")
    enterButton.setEnabled(false)
  }

  def isTextValid : Boolean = {
    val txt = textField.getText.trim
    txt.matches("^[a-zA-Z0-9 \\-_]{1,30}$") && txt != "default" && !settingsWindow.colorSchemes.contains(txt)
  }

  def valueChanged = {
    if (isTextValid) {
      textField.setBackground(textFieldBg)
      enterButton.setEnabled(true)
    } else {
      enterButton.setEnabled(false)

      if (textField.getText.trim.size > 0) {
        textField.setBackground(errorBg)
      } else {
        textField.setBackground(textFieldBg)
      }
    }
  }

  textField.getDocument.addDocumentListener(new DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = valueChanged
    override def changedUpdate(e: DocumentEvent): Unit = valueChanged
    override def removeUpdate(e: DocumentEvent): Unit = valueChanged
  })

  enterButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      if (isTextValid) {
        val name = textField.getText.trim
        settingsWindow.colorSchemes(name) = new ColorSchemeConfig(name)
        settingsWindow.setMainConfigDirty
        csgp.updateColorSchemes
        clear
        setVisible(false)
      } else {
        enterButton.setEnabled(false)
      }
    }
  })

  cancelButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = {
      clear
      setVisible(false)
    }
  })

  addComponentListener(new ComponentListener {
    override def componentShown(e: ComponentEvent): Unit = clear
    override def componentHidden(e: ComponentEvent): Unit = clear
    override def componentMoved(e: ComponentEvent): Unit = {}
    override def componentResized(e: ComponentEvent): Unit = {}
  })

}

class ColorSchemeConfigPanel(val settingsWindow: SettingsWindow) extends JPanel {

  val newColorSchemeDialog = new NewColorSchemeDialog(settingsWindow, this)
  val colorSchemesBox = new JComboBox(Array("default"))
  colorSchemesBox.setSize(150, 30)
  val newColorSchemeButton = new JButton("new color scheme")

  setLayout(new BoxLayout(this, BoxLayout.Y_AXIS))

  val gridBagPanel = new JPanel
  gridBagPanel.setLayout(new GridBagLayout())

  val c = new GridBagConstraints()

  c.gridwidth = 2
  c.gridx = 0
  c.gridy = 0
  c.anchor = GridBagConstraints.EAST
  gridBagPanel.add(colorSchemesBox, c)

  c.gridx = 2
  c.gridy = 0
  c.anchor = GridBagConstraints.WEST
  c.insets = new Insets(0, 10, 0, 10)
  gridBagPanel.add(newColorSchemeButton, c)

  c.fill = GridBagConstraints.HORIZONTAL
  c.gridwidth = 1

  val colorButtons = mutable.Map[String, ColorButton]()

  private def addColorButton(label: String, color: Color, column: Int, row: Int, tooltip: Option[String] = None) = {
    c.gridx = column
    c.gridy = row
    c.insets = new Insets(0, 10, 0, 10)
    val lbl = new JLabel(label, SwingConstants.RIGHT)
    tooltip.foreach(lbl.setToolTipText)
    gridBagPanel.add(lbl, c)

    c.gridx = column + 1
    c.gridy = row
    c.insets = new Insets(0, 0, 0, 0)
    val btn = new ColorButton(label, color, this)
    gridBagPanel.add(btn, c)

    colorButtons(label) = btn
  }

  val cs = DefaultColorScheme

  addColorButton("default FG", cs.color(TelnetColorDefaultFg), 0, 1, Some("set the default foreground text color"))
  addColorButton("default BG", cs.color(TelnetColorDefaultBg), 2, 1, Some("set the default background text color"))

  addColorButton("black", cs.color(TelnetColorBlack), 0, 2)
  addColorButton("bold black", cs.boldColor(TelnetColorBlack), 2, 2)

  addColorButton("red", cs.color(TelnetColorRed), 0, 3)
  addColorButton("bold red", cs.boldColor(TelnetColorRed), 2, 3)

  addColorButton("green", cs.color(TelnetColorGreen), 0, 4)
  addColorButton("bold green", cs.boldColor(TelnetColorGreen), 2, 4)

  addColorButton("yellow", cs.color(TelnetColorYellow), 0, 5)
  addColorButton("bold yellow", cs.boldColor(TelnetColorYellow), 2, 5)

  addColorButton("blue", cs.color(TelnetColorBlue), 0, 6)
  addColorButton("bold blue", cs.boldColor(TelnetColorBlue), 2, 6)

  addColorButton("magenta", cs.color(TelnetColorMagenta), 0, 7)
  addColorButton("bold magenta", cs.boldColor(TelnetColorMagenta), 2, 7)

  addColorButton("cyan", cs.color(TelnetColorCyan), 0, 8)
  addColorButton("bold cyan", cs.boldColor(TelnetColorCyan), 2, 8)

  addColorButton("white", cs.color(TelnetColorWhite), 0, 9)
  addColorButton("bold white", cs.boldColor(TelnetColorWhite), 2, 9)

  add(gridBagPanel)

  colorSchemesBox.setSelectedIndex(0)

  def updateColorSchemes : Unit = {
    val current = selected

    val model = new DefaultComboBoxModel( settingsWindow.colorSchemes.keys.toArray )
    colorSchemesBox.setModel(model)

    if(settingsWindow.colorSchemes.contains(selected)) {
      colorSchemesBox.setSelectedItem(current)
    }

    updateSelected
  }

  def updateSelected: Unit = {
    val cs = new ConfigurableColorScheme(settingsWindow.colorSchemes(selected))

    colorButtons("default FG").color = cs.defaultFg
    colorButtons("default BG").color = cs.defaultBg

    colorButtons("black").color = cs.black
    colorButtons("red").color = cs.red
    colorButtons("green").color = cs.green
    colorButtons("yellow").color = cs.yellow
    colorButtons("blue").color = cs.blue
    colorButtons("magenta").color = cs.magenta
    colorButtons("cyan").color = cs.cyan
    colorButtons("white").color = cs.white

    colorButtons("bold black").color = cs.boldBlack
    colorButtons("bold red").color = cs.boldRed
    colorButtons("bold green").color = cs.boldGreen
    colorButtons("bold yellow").color = cs.boldYellow
    colorButtons("bold blue").color = cs.boldBlue
    colorButtons("bold magenta").color = cs.boldMagenta
    colorButtons("bold cyan").color = cs.boldCyan
    colorButtons("bold white").color = cs.boldWhite

    repaint()
  }

  def setColor(label: String, color: Color) = {
    val cs = settingsWindow.colorSchemes(selected)

    label match {
      case "default FG" => settingsWindow.colorSchemes(selected) = cs.copy(defaultFg = ColorUtils.toHex(color))
      case "default BG" => settingsWindow.colorSchemes(selected) = cs.copy(defaultBg = ColorUtils.toHex(color))

      case "black" => settingsWindow.colorSchemes(selected) = cs.copy(black = ColorUtils.toHex(color))
      case "red" => settingsWindow.colorSchemes(selected) = cs.copy(red = ColorUtils.toHex(color))
      case "green" => settingsWindow.colorSchemes(selected) = cs.copy(green = ColorUtils.toHex(color))
      case "yellow" => settingsWindow.colorSchemes(selected) = cs.copy(yellow = ColorUtils.toHex(color))
      case "blue" => settingsWindow.colorSchemes(selected) = cs.copy(blue = ColorUtils.toHex(color))
      case "magenta" => settingsWindow.colorSchemes(selected) = cs.copy(magenta = ColorUtils.toHex(color))
      case "cyan" => settingsWindow.colorSchemes(selected) = cs.copy(cyan = ColorUtils.toHex(color))
      case "white" => settingsWindow.colorSchemes(selected) = cs.copy(white = ColorUtils.toHex(color))

      case "bold black" => settingsWindow.colorSchemes(selected) = cs.copy(boldBlack = ColorUtils.toHex(color))
      case "bold red" => settingsWindow.colorSchemes(selected) = cs.copy(boldRed = ColorUtils.toHex(color))
      case "bold green" => settingsWindow.colorSchemes(selected) = cs.copy(boldGreen = ColorUtils.toHex(color))
      case "bold yellow" => settingsWindow.colorSchemes(selected) = cs.copy(boldYellow = ColorUtils.toHex(color))
      case "bold blue" => settingsWindow.colorSchemes(selected) = cs.copy(boldBlue = ColorUtils.toHex(color))
      case "bold magenta" => settingsWindow.colorSchemes(selected) = cs.copy(boldMagenta = ColorUtils.toHex(color))
      case "bold cyan" => settingsWindow.colorSchemes(selected) = cs.copy(boldCyan = ColorUtils.toHex(color))
      case "bold white" => settingsWindow.colorSchemes(selected) = cs.copy(boldWhite = ColorUtils.toHex(color))

      case _ => throw new IOException("oh shit, no color found")
    }

    settingsWindow.setMainConfigDirty
  }

  def selected = colorSchemesBox.getSelectedItem.asInstanceOf[String]

  colorSchemesBox.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = updateSelected
  })

  newColorSchemeButton.addActionListener(new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = newColorSchemeDialog.setVisible(true)
  })
}
