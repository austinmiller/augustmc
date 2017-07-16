package aug.profile

import java.awt.Color

trait ProfileEventListener {
  def event(event: ProfileEvent, data: Option[String])
}

trait ProfileInterface {
  val defaultWindow = "default"
  def send(s: String) : Unit
  def info(s: String, window: String = defaultWindow) : Unit
  def echo(s: String, color: Option[Color] = None, window: String = defaultWindow) : Unit
  def addColoredText(s: String, window: String = defaultWindow) : Unit
  def consumeNextCommand() : Unit
  def sendGmcp(s: String) : Unit
  def connected : Boolean
}

sealed trait ProfileEvent

case object TelnetConnect extends ProfileEvent
case object TelnetError extends ProfileEvent
case object TelnetDisconnect extends ProfileEvent
case object TelnetRecv extends ProfileEvent
case object TelnetGMCP extends ProfileEvent

case object ScriptClose extends ProfileEvent
case object ScriptInit extends ProfileEvent
case object ScriptNewLine extends ProfileEvent
case object ScriptFragment extends ProfileEvent
case object ScriptNewColorLine extends ProfileEvent
case object ScriptColorFragment extends ProfileEvent

case object UserCommand extends ProfileEvent

class Profile {

}
