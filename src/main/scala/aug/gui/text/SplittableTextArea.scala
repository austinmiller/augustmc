package aug.gui.text

import java.awt.Font
import java.awt.event.{MouseWheelEvent, MouseWheelListener}
import java.util.Optional
import javax.swing.JSplitPane
import javax.swing.border.EmptyBorder

import aug.io.{ColorScheme, ConfigurableColorScheme}
import aug.profile.{ConfigManager, ProfileConfig}
import aug.script.framework.{LineEvent, LineWithNum, TextWindowInterface}
import aug.util.Util

class SplittableTextArea(profileConfig: ProfileConfig, hasHighlight: HasHighlight)
  extends JSplitPane with MouseWheelListener with TextWindowInterface {
  val text = new Text(profileConfig)
  private val topTextArea = new TextArea(hasHighlight, text)
  private val textArea = new TextArea(hasHighlight, text)
  private var scrollPos : Long = 0
  private var scrollSpeed = 4
  private var splittable = true

  setOrientation(JSplitPane.VERTICAL_SPLIT)
  setDividerSize(1)
  setTopComponent(topTextArea)
  setBottomComponent(textArea)
  setFocusable(false)

  addMouseWheelListener(this)

  unsplit()

  textArea.setVisible(true)

  setBorder(new EmptyBorder(0, 0, 0, 0))

  def setProfileConfig(profileConfig: ProfileConfig): Unit = text.profileConfig = profileConfig

  def setActiveFont(font: Font): Unit = {
    setFont(font)
    topTextArea.setActiveFont(font)
    textArea.setActiveFont(font)
  }

  override def unsplit(): Unit = {
    if (isSplit) {
      topTextArea.setVisible(false)
      setDividerSize(0)
      setDividerLocation(0)
    }
  }

  def isSplit: Boolean = topTextArea.isVisible

  override def split() : Unit = {
    if (!isSplit && splittable) {
      scrollPos = text.length
      topTextArea.setBotLine(scrollPos)
      setDividerLocation(0.7)
      setDividerSize(4)
      topTextArea.setVisible(true)
    }
  }

  def handleDown() : Unit = {
    if(!isSplit) return
    val maxPos = text.length
    scrollPos += scrollSpeed

    if (scrollPos >= maxPos) unsplit() else topTextArea.setBotLine(scrollPos)
  }

  def handleUp() : Unit = {
    if(!isSplit) split() else {
      scrollPos = Math.max(scrollPos - scrollSpeed, 1)
      topTextArea.setBotLine(scrollPos)
    }
  }

  override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
    if(e.getWheelRotation < 0) handleUp() else handleDown()
    e.consume()
  }

  override def echo(line: String): Unit = {
    text.addLine(line)
    repaint()
  }

  override def echo(lines: Array[String]): Unit = {
    lines.foreach(l=> text.addLine(l))
    repaint()
  }

  override def clear(): Unit = {
    text.clear()
    repaint()
  }

  override def setSplittable(splittable: Boolean): Unit = {
    this.splittable = splittable
    if (!splittable) unsplit()
  }

  override def setLine(lineWithNum: LineWithNum): Unit = {
    text.setLine(lineWithNum.lineNum, lineWithNum.line)
    repaint()
  }

  override def setHighlightable(highlightable: Boolean): Unit = {
    textArea.setHighlightable(highlightable)
    topTextArea.setHighlightable(highlightable)
  }

  private def getColorScheme(colorSchemeName: String): Option[ColorScheme] = {
    ConfigManager.getMainConfig.colorSchemes.find(_.name == colorSchemeName).map(new ConfigurableColorScheme(_))
  }

  override def setColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach {cs =>
      textArea.setColorScheme(cs)
      topTextArea.setColorScheme(cs)
    }
  }

  override def setTextFont(fontName: String, size: Int): Unit = {

    if (!Util.fontSizes.contains(size))
      throw new RuntimeException(s"Font size $size is not in list ${Util.fontSizes}.")

    if (!Util.monospaceFamilies.contains(fontName))
      throw new RuntimeException(s"Font $fontName is not in list ${Util.monospaceFamilies}.")

    setActiveFont(new Font(fontName, 0, size))
  }


  override def getFontSizes: Array[Int] = Util.fontSizes

  override def getFonts: Array[String] = Util.monospaceFamilies.toArray

  override def setTopColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach(topTextArea.setColorScheme)
  }

  override def setBottomColorScheme(colorSchemeName: String): Unit = {
    getColorScheme(colorSchemeName).foreach(textArea.setColorScheme)
  }

  override def getLine(lineNum: Long): Optional[LineEvent] = {
    val opt: Option[LineEvent] = text.get(lineNum).map(_.colorStr).map{ raw =>
      new LineEvent(lineNum, raw)
    }
    Optional.of(opt.orNull)
  }

  override def setLines(lines: Array[LineWithNum]): Unit = {
    text.setLines(lines)
    repaint()
  }
}
