package aug.io

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.zip.Inflater

import aug.profile._
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.util.{Failure, Try}

sealed trait TelnetState

case object Stream extends TelnetState
case object Command extends TelnetState
case object Option extends TelnetState
case object SubNegotiation extends TelnetState


sealed abstract class TelnetCommand(val code: Byte, val text: String)

case class TelnetUnknown(val unknownCode: Byte) extends TelnetCommand(unknownCode,"UNKNOWN")
case object TelnetEsc extends TelnetCommand(27.toByte,"ESC")
case object TelnetSE extends TelnetCommand(240.toByte,"SE")
case object TelnetSB extends TelnetCommand(250.toByte,"SB")
case object TelnetWill extends TelnetCommand(251.toByte,"WILL")
case object TelnetWont extends TelnetCommand(252.toByte,"WONT")
case object TelnetDo extends TelnetCommand(253.toByte,"DO")
case object TelnetDont extends TelnetCommand(254.toByte,"DONT")
case object TelnetIac extends TelnetCommand(255.toByte,"IAC")

sealed abstract class TelnetOption(val code: Byte, val text: String)

case object OptionEcho extends TelnetOption(1.toByte,"ECHO")
case object OptionType extends TelnetOption(24.toByte,"TYPE")
case object OptionWinSize extends TelnetOption(31.toByte,"WIN_SIZE")
case object OptionMccp1 extends TelnetOption(85.toByte,"MCCP1")
case object OptionMccp2 extends TelnetOption(86.toByte,"MCCP2")
case object OptionAard102 extends TelnetOption(102.toByte,"AARD102")
case object OptionAtcp extends TelnetOption(200.toByte,"ATCP")
case object OptionGmcp extends TelnetOption(201.toByte,"GMCP")
case class OptionUnknown(val unknownCode: Byte) extends TelnetOption(unknownCode,"UNKNOWN")


object Telnet {
  val log = Logger(LoggerFactory.getLogger(Telnet.getClass))

  val telnetCommands : Map[Byte,TelnetCommand] = Map(
    TelnetEsc.code -> TelnetEsc,
    TelnetSE.code -> TelnetSE,
    TelnetSB.code -> TelnetSB,
    TelnetWill.code -> TelnetWill,
    TelnetWont.code -> TelnetWont,
    TelnetDo.code -> TelnetDo,
    TelnetDont.code -> TelnetDont,
    TelnetIac.code -> TelnetIac)

  val options : Map[Byte,TelnetOption] = Map(
    OptionEcho.code -> OptionEcho,
    OptionType.code -> OptionType,
    OptionWinSize.code -> OptionWinSize,
    OptionMccp1.code -> OptionMccp1,
    OptionMccp2.code -> OptionMccp2,
    OptionAard102.code -> OptionAard102,
    OptionAtcp.code -> OptionAtcp,
    OptionGmcp.code -> OptionGmcp
  )

  val CHARSET			= Charset.forName("US-ASCII");

  val COLOR_DEFAULT	= 0;

  val TELQUAL_IS		= 0;
  val TELQUAL_SEND	= 1;
}

class Telnet(profile: Profile, val profileConfig: ProfileConfig) extends
  AbstractConnection(new InetSocketAddress(profileConfig.telnetConfig.host,
    profileConfig.telnetConfig.port)) {
  import Telnet.log

  val url = profileConfig.telnetConfig.host
  val port = profileConfig.telnetConfig.port

  private val inflater = new Inflater()
  private val inflateBuffer = ByteBuffer.allocate(1<<16)
  private val postBuffer = ByteBuffer.allocate(1<<16)

  private var compressed = false
  private var state : TelnetState = Stream
  private var command : TelnetCommand = TelnetUnknown(0)
  private var option : TelnetOption = new OptionUnknown(0)
  private var subMessage = new StringBuilder
  private var zipinput : Array[Byte] = null
  private var rawBuffer = ByteBuffer.allocate(1<<16)
  private var marker = 0

  override def close = {
    super.close
    profile.offer(TelnetDisconnect)
  }

  override def connect = {
    log.info(f"connecting to $url:$port")
    super.connect
  }

  private def constructCommand(command: TelnetCommand, option: TelnetOption) : Array[Byte] = {
    Array(TelnetIac.code,command.code,option.code)
  }

  override def finishConnect : Unit = {
    super.finishConnect
    if(!isClosed) profile.offer(TelnetConnect)
  }

  private def handleByte(c: Byte) : Unit = {
    val v : Byte = (0x00FF & c).toByte

    if (!postBuffer.hasRemaining()) post

    state match {

      case Stream =>
        if(TelnetIac.code == v) {
          state = Command
        } else if('\r' != c) postBuffer.put(c)

      case Command =>
        command = Telnet.telnetCommands(v)

        if(TelnetSE.code == v) {
          state = Stream
          handleSubNegotiation
        } else {
          state = Option
        }

      case Option =>
        option = Telnet.options.get(v).getOrElse { new OptionUnknown(v)}
        handleOption

      case SubNegotiation =>
        if(TelnetIac.code == v) {
          state = Command
        } else subMessage.append(c.toChar)
    }
  }

  private def handleDoOption = {
    log.debug("recv: IAC {} {}", command.text, option.text)
    option match {
      case OptionType => send(TelnetWill,OptionType)
      case ou: OptionUnknown => send(TelnetWont,ou)
      case _ =>
    }

  }

  override def handleIncoming(bytes: Array[Byte]) : Unit = {
    log.trace("{} incoming bytes", bytes.length)

    inflate(bytes)

    handleIncomingLoop

    post

  }

  private def handleIncomingLoop() : Unit = {
    while(true) {
      while(rawBuffer.hasRemaining) {
        handleByte(rawBuffer.get)
      }

      if(!compressed || inflater.needsInput) {
        log.trace("breaking byte loop")
        return
      }

      readRaw
    }
  }

  private def handleOption : Unit = {
    command match {
      case TelnetSB => state = SubNegotiation
      case TelnetWill =>
        state = Stream
        handleWillOption
      case TelnetDo =>
        state = Stream
        handleDoOption
      case _ => state = Stream
    }
  }

  private def handleSubNegotiation() = {
    val sm = subMessage.toString
    subMessage = new StringBuilder
    log.trace("subMessage {}", sm)

    option match {
      case OptionType =>
        log.debug("recv: IAC {} {}",command.text, option.text)
        val i = sm.charAt(0)
        if(Telnet.TELQUAL_SEND == i) {
          send(OptionType, Telnet.TELQUAL_IS+"augustMC")
        }
      case OptionMccp2 => startCompression()
      case OptionGmcp => profile.offer(TelnetGMCP(sm))
      case _ =>
    }
  }

  private def handleWillOption = {
    log.debug("recv: IAC {} {}", command.text,option.text);
    option match {
      case OptionEcho => send(TelnetDo,OptionEcho)
      case OptionMccp2 => send(TelnetDo,OptionMccp2)
      case OptionAtcp => send(TelnetDo,OptionAtcp)
      case OptionGmcp =>
        send(TelnetDo, OptionGmcp)
        // TODO don't easy code this
        send(OptionGmcp, "core.supports.set [\"core 1\",\"comm 1\",\"group 1\",\"room 1\",\"char 1\"]")
      case _ =>
    }

  }

  private def inflate(bytes: Array[Byte]) : Unit = {
    if(!compressed) {
      rawBuffer = ByteBuffer.wrap(bytes)
    } else {
      zipinput = Util.concatenate(zipinput,bytes)
      readRaw
    }
  }

  private def post : Unit = {
    if(postBuffer.position == 0) return

    val msg = new String(postBuffer.array, 0, postBuffer.position)

    profile.offer(TelnetRecv(msg))
    postBuffer.position(0)
  }

  private def readRaw : Unit = {
    log.trace("zipinput.length == {}",zipinput.length)

    inflater.setInput(zipinput)

    if(inflater.needsInput) return

    inflateBuffer.clear()

    Try {
      val read = inflater.inflate(inflateBuffer.array)
      inflateBuffer.limit(read)
      rawBuffer = inflateBuffer.slice
    } match {
      case Failure(e) => {
        log.error("failed on inflate",e)
        close
        return
      }
      case _ =>
    }

    val remaining = inflater.getRemaining
    zipinput = Util.right(zipinput,remaining)

    log.trace("readRaw: {}",remaining)
  }

  override def select = {}

  def send(command: TelnetCommand, option: TelnetOption): Unit = {
    log.debug("send: IAC {} {}", command.text, option.text)
    send(constructCommand(command, option))
  }

  def send(option: TelnetOption, message: String): Unit = {
    val cmd : Array[Byte] = constructCommand(TelnetSB, option)
    val stop : Array[Byte] = Array(TelnetIac.code, TelnetSE.code)

    send(Util.concatenate(cmd, message.getBytes, stop))
  }

  private def startCompression() : Unit = {
    compressed = true
    zipinput = new Array[Byte](rawBuffer.remaining)
    System.arraycopy(rawBuffer.array, rawBuffer.position, zipinput, 0, zipinput.length)
    log.debug("starting MCCP compression, zipinput length == {}", zipinput.length)

    rawBuffer = ByteBuffer.allocate(0)

    readRaw
  }

  override def error(msg: String): Unit = {
    profile.offer(TelnetError(msg))
  }
}
