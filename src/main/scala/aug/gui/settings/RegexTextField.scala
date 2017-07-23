package aug.gui.settings

import java.awt.Color
import javax.swing.JTextField
import javax.swing.event.{DocumentEvent, DocumentListener}

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
