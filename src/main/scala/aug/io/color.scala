package aug.io

import java.awt.Color

import aug.profile.ColorSchemeConfig


sealed trait TelnetColor

case object TelnetColorDefaultFg extends TelnetColor
case object TelnetColorDefaultBg extends TelnetColor
case object TelnetColorBlack extends TelnetColor
case object TelnetColorRed extends TelnetColor
case object TelnetColorGreen extends TelnetColor
case object TelnetColorYellow extends TelnetColor
case object TelnetColorBlue extends TelnetColor
case object TelnetColorMagenta extends TelnetColor
case object TelnetColorCyan extends TelnetColor
case object TelnetColorWhite extends TelnetColor

case class ColorCode(fg: TelnetColor, bg: TelnetColor = TelnetColorDefaultBg, bold: Boolean = false) {
  def fgColor(colorScheme: ColorScheme) = if (bold) colorScheme.boldColor(fg) else colorScheme.color(fg)
  def bgColor(colorScheme: ColorScheme) = colorScheme.color(bg)
}

object DefaultColorCode extends ColorCode(TelnetColorDefaultFg, TelnetColorDefaultBg, false)
object HighlightColorCode extends ColorCode(TelnetColorDefaultBg, TelnetColorDefaultFg, false)

trait ColorScheme {
  def boldColor(telnetColor: TelnetColor) : Color
  def color(telnetColor: TelnetColor) : Color
}

object DefaultColorScheme extends ColorScheme {
  override def color(telnetColor: TelnetColor): Color = {
    telnetColor match {
      case TelnetColorDefaultBg => new Color(0, 0, 0)
      case TelnetColorDefaultFg => new Color(170, 170, 170)
      case TelnetColorBlack => new Color(0, 0, 0)
      case TelnetColorRed => new Color(255, 0, 0)
      case TelnetColorGreen => new Color(0, 170, 0)
      case TelnetColorYellow => new Color(170, 85, 0)
      case TelnetColorBlue => new Color(0, 0, 170)
      case TelnetColorMagenta => new Color(170, 0, 170)
      case TelnetColorCyan => new Color(0, 170, 170)
      case TelnetColorWhite => new Color(170, 170, 170)
    }
  }

  override def boldColor(telnetColor: TelnetColor): Color = {
    telnetColor match {
      case TelnetColorDefaultBg => new Color(0, 0, 0)
      case TelnetColorDefaultFg => new Color(170, 170, 170)
      case TelnetColorBlack => new Color(85, 85, 85)
      case TelnetColorRed => new Color(255, 85, 85)
      case TelnetColorGreen => new Color(85, 255, 85)
      case TelnetColorYellow => new Color(255, 255, 85)
      case TelnetColorBlue => new Color(85, 85, 255)
      case TelnetColorMagenta => new Color(255, 85, 255)
      case TelnetColorCyan => new Color(85, 255, 255)
      case TelnetColorWhite => new Color(255, 255, 255)
    }
  }
}

class ConfigurableColorScheme(colorSchemeConfig: ColorSchemeConfig) extends ColorScheme {

  val defaultFg = Color.decode(colorSchemeConfig.defaultFg)
  val defaultBg = Color.decode(colorSchemeConfig.defaultBg)
  val black = Color.decode(colorSchemeConfig.black)
  val red = Color.decode(colorSchemeConfig.red)
  val green = Color.decode(colorSchemeConfig.green)
  val yellow = Color.decode(colorSchemeConfig.yellow)
  val blue = Color.decode(colorSchemeConfig.blue)
  val magenta = Color.decode(colorSchemeConfig.magenta)
  val cyan = Color.decode(colorSchemeConfig.cyan)
  val white = Color.decode(colorSchemeConfig.white)

  val boldBlack = Color.decode(colorSchemeConfig.boldBlack)
  val boldRed = Color.decode(colorSchemeConfig.boldRed)
  val boldGreen = Color.decode(colorSchemeConfig.boldGreen)
  val boldYellow = Color.decode(colorSchemeConfig.boldYellow)
  val boldBlue = Color.decode(colorSchemeConfig.boldBlue)
  val boldMagenta = Color.decode(colorSchemeConfig.boldMagenta)
  val boldCyan = Color.decode(colorSchemeConfig.boldCyan)
  val boldWhite = Color.decode(colorSchemeConfig.boldWhite)

  override def boldColor(telnetColor: TelnetColor): Color = {
    telnetColor match {
      case TelnetColorDefaultFg => defaultFg
      case TelnetColorDefaultBg => defaultBg
      case TelnetColorBlack => boldBlack
      case TelnetColorRed => boldRed
      case TelnetColorGreen => boldGreen
      case TelnetColorYellow => boldYellow
      case TelnetColorBlue => boldBlue
      case TelnetColorMagenta => boldMagenta
      case TelnetColorCyan => boldCyan
      case TelnetColorWhite => boldWhite
    }
  }

  override def color(telnetColor: TelnetColor): Color = {
    telnetColor match {
      case TelnetColorDefaultFg => defaultFg
      case TelnetColorDefaultBg => defaultBg
      case TelnetColorBlack => black
      case TelnetColorRed => red
      case TelnetColorGreen => green
      case TelnetColorYellow => yellow
      case TelnetColorBlue => blue
      case TelnetColorMagenta => magenta
      case TelnetColorCyan => cyan
      case TelnetColorWhite => white
    }
  }
}

object SidePanelColor extends Color(62, 67, 76)
object BorderColor extends Color(85, 90, 92)
object TransparentColor extends Color(0, 0, 0, 0)