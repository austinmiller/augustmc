package aug.io

import java.awt.Color


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


case class ColorCode(fg: TelnetColor, bg: TelnetColor, bold: Boolean) {
  def fgColor(colorScheme: ColorScheme) = if (bold) colorScheme.boldColor(fg) else colorScheme.color(fg)
  def bgColor(colorScheme: ColorScheme) = colorScheme.color(bg)
}

object DefaultColorCode extends ColorCode(TelnetColorDefaultFg, TelnetColorDefaultBg, false)

trait ColorScheme {
  def boldColor(telnetColor: TelnetColor) : Color
  def color(telnetColor: TelnetColor) : Color
}

object DefaultColorScheme extends ColorScheme {
  override def boldColor(telnetColor: TelnetColor): Color = {
    telnetColor match {
      case TelnetColorDefaultBg => new Color(0, 0, 0)
      case TelnetColorDefaultFg => new Color(170, 170, 170)
      case TelnetColorBlack => new Color(0, 0, 0)
      case TelnetColorRed => new Color(0, 0, 0)
      case TelnetColorGreen => new Color(0, 170, 0)
      case TelnetColorYellow => new Color(170, 85, 0)
      case TelnetColorBlue => new Color(0, 0, 170)
      case TelnetColorMagenta => new Color(170, 0, 170)
      case TelnetColorCyan => new Color(0, 170, 170)
      case TelnetColorWhite => new Color(170, 170, 170)
    }
  }

  override def color(telnetColor: TelnetColor): Color = {
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
