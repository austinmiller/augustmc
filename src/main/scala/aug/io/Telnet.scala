package aug.io

import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Inflater
import aug.profile._
import aug.util.Util
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import java.nio.ByteBuffer
import scala.util.{Failure, Try}

sealed trait TelnetState

case object Stream extends TelnetState
case object Command extends TelnetState
case object Option extends TelnetState
case object SubNegotiation extends TelnetState

sealed abstract class TelnetCommand(val code: Byte, val text: String)

case class TelnetUnknown(unknownCode: Byte) extends TelnetCommand(unknownCode,"UNKNOWN")
case object TelnetEOR extends TelnetCommand(25.toByte,"EOR")
case object TelnetEsc extends TelnetCommand(27.toByte,"ESC")
case object TelnetSE extends TelnetCommand(240.toByte,"SE")
case object TelnetGA extends TelnetCommand(249.toByte, "GA")
case object TelnetSB extends TelnetCommand(250.toByte,"SB")
case object TelnetWill extends TelnetCommand(251.toByte,"WILL")
case object TelnetWont extends TelnetCommand(252.toByte,"WONT")
case object TelnetDo extends TelnetCommand(253.toByte,"DO")
case object TelnetDont extends TelnetCommand(254.toByte,"DONT")
case object TelnetIac extends TelnetCommand(255.toByte,"IAC")

sealed abstract class TelnetOption(val code: Byte, val text: String)

case object OptionEcho extends TelnetOption(1.toByte,"ECHO")
case object OptionType extends TelnetOption(24.toByte,"TYPE")
case object OptionEOR extends TelnetOption(25.toByte,"EOR")
case object OptionWinSize extends TelnetOption(31.toByte,"WIN_SIZE")
case object OptionMccp1 extends TelnetOption(85.toByte,"MCCP1")
case object OptionMccp2 extends TelnetOption(86.toByte,"MCCP2")
case object OptionAard102 extends TelnetOption(102.toByte,"AARD102")
case object OptionAtcp extends TelnetOption(200.toByte,"ATCP")
case object OptionGmcp extends TelnetOption(201.toByte,"GMCP")
case class OptionUnknown(unknownCode: Byte) extends TelnetOption(unknownCode,"UNKNOWN")

object Telnet {
  private val log = Logger(LoggerFactory.getLogger(Telnet.getClass))

  private val telnetCommands : Map[Byte,TelnetCommand] = Map(
    TelnetEOR.code -> TelnetEOR,
    TelnetEsc.code -> TelnetEsc,
    TelnetSE.code -> TelnetSE,
    TelnetGA.code -> TelnetGA,
    TelnetSB.code -> TelnetSB,
    TelnetWill.code -> TelnetWill,
    TelnetWont.code -> TelnetWont,
    TelnetDo.code -> TelnetDo,
    TelnetDont.code -> TelnetDont,
    TelnetIac.code -> TelnetIac)

  private val options : Map[Byte, TelnetOption] = Map(
    OptionEcho.code -> OptionEcho,
    OptionType.code -> OptionType,
    OptionWinSize.code -> OptionWinSize,
    OptionMccp1.code -> OptionMccp1,
    OptionMccp2.code -> OptionMccp2,
    OptionAard102.code -> OptionAard102,
    OptionAtcp.code -> OptionAtcp,
    OptionGmcp.code -> OptionGmcp
  )

  private val CHARSET			= Charset.forName("US-ASCII")

  private val COLOR_DEFAULT	= 0

  private val TELQUAL_IS		= 0
  private val TELQUAL_SEND	= 1

  private val idGenerator = new AtomicLong()
}

class Telnet(profile: Profile, val profileConfig: ProfileConfig) extends
  AbstractConnection(new InetSocketAddress(profileConfig.telnetConfig.host,
    profileConfig.telnetConfig.port)) {
  private val log = Telnet.log

  val url: String = profileConfig.telnetConfig.host
  val port: Int = profileConfig.telnetConfig.port
  val id: Long = Telnet.idGenerator.incrementAndGet()

  private val inflater = new Inflater()
  private val inflateBuffer = ByteBuffer.allocate(1<<16)
  private val postBuffer: ByteBuffer = ByteBuffer.allocate(1<<16)

  private var compressed = false
  private var state : TelnetState = Stream
  private var command : TelnetCommand = TelnetUnknown(0)
  private var option : TelnetOption = OptionUnknown(0)
  private var subMessage = new StringBuilder
  private var zipinput : Array[Byte] = _
  private var rawBuffer = ByteBuffer.allocate(1<<16)

  override def close(): Unit = {
    super.close()
  }

  override def connect(): Unit = {
    log.info(f"connecting to $url:$port")
    super.connect()
  }

  private def constructCommand(command: TelnetCommand, option: TelnetOption) : Array[Byte] = {
    Array(TelnetIac.code, command.code, option.code)
  }

  override def onConnect(): Unit = {
    super.onConnect()
    log.info(s"finished connect to $url:$port, closed ==")
    if (!isClosed) profile.offer(TelnetConnect(id, url, port))
  }

  private def handleByte(c: Byte): Unit = {
    val v : Byte = (0x00FF & c).toByte

    if (!postBuffer.hasRemaining) post()

    state match {

      case Stream =>
        if(TelnetIac.code == v) {
          state = Command
        } else if('\r' != c) postBuffer.put(c)

      case Command =>
        command = Telnet.telnetCommands.getOrElse(v, TelnetUnknown(v))

        command match {
          case TelnetSE =>
            state = Stream
            handleSubNegotiation()

          case TelnetDo | TelnetDont | TelnetWill | TelnetWont | TelnetSB =>
            state = Option

          case TelnetUnknown(_) =>
            log.info(s"unknown telnet command: $v")
            profile.slog.info(s"unknown telnet command: $v")

          case TelnetGA =>
            post(true)
            state = Stream

          case all =>
            state = Stream
            log.error(s"unhandled command $all")
        }

      case Option =>
        option = Telnet.options.getOrElse(v, OptionUnknown(v))
        handleOption()

      case SubNegotiation =>
        if(TelnetIac.code == v) {
          state = Command
        } else subMessage.append(c.toChar)
    }
  }

  private def handleDoOption(): Unit = {
    log.debug("recv: IAC {} {}", command.text, option.text)
    option match {
      case OptionType => send(TelnetWill, OptionType)
      case ou: OptionUnknown => send(TelnetWont, ou)
      case _ =>
    }

  }

  override def handleIncoming(bytes: Array[Byte]): Unit = {
    log.trace("{} incoming bytes", bytes.length)

    inflate(bytes)

    handleIncomingLoop()

    post()
  }

  private def handleIncomingLoop(): Unit = {
    while(true) {
      while(rawBuffer.hasRemaining) {
        handleByte(rawBuffer.get)
      }

      if(!compressed || inflater.needsInput) {
        log.trace("breaking byte loop")
        return
      }

      readRaw()
    }
  }

  private def handleOption(): Unit = {
    command match {
      case TelnetSB =>
        state = SubNegotiation

      case TelnetWill =>
        state = Stream
        handleWillOption()

      case TelnetDo =>
        state = Stream
        handleDoOption()

      case _ => state = Stream
    }
  }

  private def handleSubNegotiation(): Unit = {
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

      case OptionMccp2 =>
        log.trace("starting compression")
        startCompression()

      case OptionGmcp =>
        log.trace(s"recv gmcp: $sm")
        profile.offer(TelnetGMCP(sm))

      case _ =>
    }
  }

  private def handleWillOption(): Unit = {
    log.debug("recv: IAC {} {}", command.text,option.text)
    option match {
      case OptionEcho => send(TelnetDo ,OptionEcho)
      case OptionMccp2 =>
        if (profileConfig.telnetConfig.mccpEnabled) {
          send(TelnetDo,OptionMccp2)
        } else send(TelnetDont, OptionMccp2)

      case OptionAtcp => send(TelnetDo, OptionAtcp)
      case OptionGmcp =>
        if (profileConfig.telnetConfig.gmcpEnabled) {
          send(TelnetDo, OptionGmcp)
          val supports = profileConfig.telnetConfig.gmcpSupports.trim
          if (supports.length > 0) {
            send(OptionGmcp, s"core.supports.set [$supports]")
          }
          //aard exa: send(OptionGmcp, "core.supports.set [\"core 1\",\"comm 1\",\"group 1\",\"room 1\",\"char 1\"]")
        } else send(TelnetDont, OptionGmcp)

      case _ =>
    }

  }

  private def inflate(bytes: Array[Byte]): Unit = {
    if (!compressed) {
      rawBuffer = ByteBuffer.wrap(bytes)
    } else {
      zipinput = Util.concatenate(zipinput,bytes)
      readRaw()
    }
  }

  private def post(withGA: Boolean = false): Unit = {
    if (postBuffer.position() == 0 && !withGA) return

    val msg = new String(postBuffer.array, 0, postBuffer.position())

    profile.offer(TelnetRecv(msg, withGA))
    postBuffer.position(0)
  }

  private def readRaw(): Unit = {
    log.trace("zipinput.length == {}",zipinput.length)

    inflater.setInput(zipinput)

    if(inflater.needsInput) return

    inflateBuffer.clear()

    Try {
      val read = inflater.inflate(inflateBuffer.array)
      inflateBuffer.limit(read)
      rawBuffer = inflateBuffer.slice
    } match {
      case Failure(e) =>
        log.error("failed on inflate",e)
        close()
        return

      case _ =>
    }

    val remaining = inflater.getRemaining
    zipinput = Util.right(zipinput,remaining)

    log.trace("readRaw: {}",remaining)
  }

  def send(command: TelnetCommand, option: TelnetOption): Unit = {
    log.debug("send: IAC {} {}", command.text, option.text)
    send(constructCommand(command, option))
  }

  def send(option: TelnetOption, message: String): Unit = {
    val cmd : Array[Byte] = constructCommand(TelnetSB, option)
    val stop : Array[Byte] = Array(TelnetIac.code, TelnetSE.code)
    log.info(s"sending $option: $message")

    send(Util.concatenate(cmd, message.getBytes, stop))
  }

  private def startCompression() : Unit = {
    compressed = true
    zipinput = new Array[Byte](rawBuffer.remaining)
    System.arraycopy(rawBuffer.array, rawBuffer.position, zipinput, 0, zipinput.length)
    log.debug("starting MCCP compression, zipinput length == {}", zipinput.length)

    rawBuffer = ByteBuffer.allocate(0)

    readRaw()
  }

  override def error(msg: String): Unit = {
    profile.offer(TelnetError(msg))
  }

  override def toString: String = s"Telnet [$id, $url:$port]"

  override def onDisconnect(): Unit = {
    super.onDisconnect()
    profile.offer(TelnetDisconnect(id))
  }
}
