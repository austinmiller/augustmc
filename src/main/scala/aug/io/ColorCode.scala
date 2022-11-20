package aug.io

import java.awt.Color

import aug.profile.ColorSchemeConfig
import aug.script.framework.tools.ScalaUtils


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
  def fgColor(colorScheme: ColorScheme): Color = if (bold) colorScheme.boldColor(fg) else colorScheme.color(fg)
  def bgColor(colorScheme: ColorScheme): Color = colorScheme.color(bg)

  def toTelnetCode: String = {
    val fgc: String = {
      val tmp: String = fg match {
        case TelnetColorDefaultFg => "0"
        case TelnetColorDefaultBg => "0"
        case TelnetColorBlack => "30"
        case TelnetColorRed => "31"
        case TelnetColorGreen => "32"
        case TelnetColorYellow => "33"
        case TelnetColorBlue => "34"
        case TelnetColorMagenta => "35"
        case TelnetColorCyan => "36"
        case TelnetColorWhite => "37"
      }

      if (bold) "1;" + tmp else tmp
    }

    val bgc: String = fg match {
      case TelnetColorDefaultFg => ""
      case TelnetColorDefaultBg => ""
      case TelnetColorBlack => "40"
      case TelnetColorRed => "41"
      case TelnetColorGreen => "42"
      case TelnetColorYellow => "43"
      case TelnetColorBlue => "44"
      case TelnetColorMagenta => "45"
      case TelnetColorCyan => "46"
      case TelnetColorWhite => "47"
    }

    val fc = if (fgc == "0" && bgc == "0") "0" else if (bgc != "") fgc + ";" + bgc else fgc
    ScalaUtils.encodeColor(fc)
  }
}

object CommandColorCode extends ColorCode(TelnetColorYellow)
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

  val defaultFg: Color = Color.decode(colorSchemeConfig.defaultFg)
  val defaultBg: Color = Color.decode(colorSchemeConfig.defaultBg)
  val black: Color = Color.decode(colorSchemeConfig.black)
  val red: Color = Color.decode(colorSchemeConfig.red)
  val green: Color = Color.decode(colorSchemeConfig.green)
  val yellow: Color = Color.decode(colorSchemeConfig.yellow)
  val blue: Color = Color.decode(colorSchemeConfig.blue)
  val magenta: Color = Color.decode(colorSchemeConfig.magenta)
  val cyan: Color = Color.decode(colorSchemeConfig.cyan)
  val white: Color = Color.decode(colorSchemeConfig.white)

  val boldBlack: Color = Color.decode(colorSchemeConfig.boldBlack)
  val boldRed: Color = Color.decode(colorSchemeConfig.boldRed)
  val boldGreen: Color = Color.decode(colorSchemeConfig.boldGreen)
  val boldYellow: Color = Color.decode(colorSchemeConfig.boldYellow)
  val boldBlue: Color = Color.decode(colorSchemeConfig.boldBlue)
  val boldMagenta: Color = Color.decode(colorSchemeConfig.boldMagenta)
  val boldCyan: Color = Color.decode(colorSchemeConfig.boldCyan)
  val boldWhite: Color = Color.decode(colorSchemeConfig.boldWhite)

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