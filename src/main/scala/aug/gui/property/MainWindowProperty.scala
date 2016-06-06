package aug.gui.property

sealed abstract class MainWindowProperty(val key: String, val defaultValue: String)

case object MWPosX extends MainWindowProperty("main.pos.x","50")
case object MWPosY extends MainWindowProperty("main.pos.y","50")
case object MWMaximized extends MainWindowProperty("main.maximized","false")
case object MWHeight extends MainWindowProperty("main.height","600")
case object MWWidth extends MainWindowProperty("main.width","800")

object MWProperties {
  val properties = Set(MWPosX,MWPosY,MWMaximized,MWHeight,MWWidth)
}
