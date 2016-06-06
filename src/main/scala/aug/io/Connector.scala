package aug.io

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import java.nio.channels._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{Failure, Try}


trait Connector {
  def select : Unit
  def finishConnect : Unit
  def read : Unit
  def write : Unit
  def address : InetSocketAddress
  def setSocketChannel(channel: SocketChannel) : Unit
}

abstract class AbstractConnection(val address: InetSocketAddress) extends Connector with AutoCloseable {

  val log = ConnectionManager.log
  var channel : SocketChannel = null
  val closed = new AtomicBoolean(false)
  val in = ByteBuffer.allocate(2<<20)
  val queue = new ConcurrentLinkedQueue[Array[Byte]]()
  var out : Option[ByteBuffer] = None

  def isClosed = closed.get

  override def close() : Unit = {
    if(!closed.compareAndSet(false,true)) return

    queue.clear
    Try {
      if(channel != null) channel.close
    } match {
      case Failure(e) => log.warn("failed to close socket",e)
      case _ =>
    }
    log.info("closed socket")
  }

  def connect = {
    Try {
      ConnectionManager.register(this)
    } match {
      case Failure(e) => close
      case _ =>
    }
  }

  override def finishConnect : Unit = {
    Try {
      if(isClosed) throw new IOException("closed")
      if(!channel.isOpen || !channel.isConnected) throw new IOException("channel is not open or connected")
    } match {
      case Failure(e) =>
        log.error("failed to finish connection",e)
        close
      case _ =>
    }
  }

  def send(msg : Array[Byte]) {

    if (!isClosed) {
      queue.add(msg)
    }
  }

  def send(s: String) : Unit = send(s.getBytes())

  override def setSocketChannel(channel: SocketChannel) = this.channel = channel

  protected def handleIncoming(bytes: Array[Byte]) : Unit

  override def read : Unit = {
    Try {
      in.clear

      val read = channel.read(in)

      if(read < 0) throw new IOException("connection reset by peer")

      in.flip

      if(!in.hasRemaining) return

      val copy = new Array[Byte](in.limit)
      System.arraycopy(in.array(),0,copy,0,copy.length)
      handleIncoming(copy)
    } match {
      case Failure(e: IOException) => {
        log.info("connection reset by peer")
        close
      }
      case Failure(e) => {
        log.error("error on read, closing",e)
        close
      }
      case _ =>
    }
  }

  override def write() : Unit = {
    Try {
      while(queue.size() > 0) {
        if(out.isEmpty || !out.get.hasRemaining) {

          out = Some(ByteBuffer.wrap(queue.poll))

          log.trace("sending: {}", new String(out.get.array))
        }

        val written = channel.write(out.get)

        log.trace(s"$channel wrote $written bytes")

        if(written == 0) return
      }
    } match {
      case Failure(e) => {
        log.error("error on write, closing",e)
        close
      }
      case _ =>
    }
  }

}

trait Server {
  def accept : Unit
  def setServerSocketChannel(channel: ServerSocketChannel)
  def address : InetSocketAddress
}


object ConnectionManager extends AutoCloseable with Runnable  {
  val log = Logger(LoggerFactory.getLogger(ConnectionManager.getClass))

  private val closed = new AtomicBoolean(false)
  private val selector : Selector = Selector.open
  private val thread	= new Thread(this, "TelnetManager");
  private val connectors  = new scala.collection.mutable.ListBuffer[Connector]

  def register(connector: Connector) : Unit = register(connector,None)

  def register(connector: Connector, channel: Option[SocketChannel]): Unit = {
    val ops = if(channel.isEmpty) SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT else
      SelectionKey.OP_READ | SelectionKey.OP_WRITE
    val ch = channel.getOrElse { SocketChannel.open(connector.address) }

    ch.configureBlocking(false)
    ch.socket().setSendBufferSize(0x100000) // 1Mb
    ch.socket().setReceiveBufferSize(0x100000) // 1Mb
    ch.socket().setKeepAlive(true)
    ch.socket().setReuseAddress(true)
    ch.socket().setSoLinger(false, 0)
    ch.socket().setSoTimeout(0)
    ch.socket().setTcpNoDelay(true)

    connector.setSocketChannel(ch)

    connectors += connector
    ch.register(selector, ops, connector);

    if( log.underlying.isDebugEnabled) {
      selector.keys.asScala.foreach { sk => log.debug("class={}", sk.attachment.getClass.getCanonicalName) }
    }

    log.debug("connector registered: {}", channel);
  }

  def start() : Unit = {
    log.info("starting")
    thread.start()
  }

  override def run() : Unit = {
    while(closed.get != true && selector.isOpen) {
      Try {
        selectCallback
        selector.selectNow
        processKeys
        Thread.sleep(50)
      } match {
        case Failure(e) => log.error("exception caught during selection",e)
        case _ =>
      }
    }
    selector.close
  }

  private def selectCallback = {
    connectors foreach {_.select}
  }

  private def processKeys = {
    selector.selectedKeys.asScala foreach { processKey(_) }
  }

  private def processKey(key : SelectionKey): Unit = {
    if(!key.isValid) {
      key.cancel()
      return
    }

    if(key.isAcceptable) key.attachment.asInstanceOf[Server].accept
    if(key.isConnectable) key.attachment.asInstanceOf[Connector].finishConnect

    if(key.isReadable) {
      Try {
        key.attachment.asInstanceOf[Connector].read
      } match {
        case Failure(e) => {
          log.info("closing {}", key.attachment)
          key.cancel
          return
        }
        case _ =>
      }
    }

    if(key.isValid && key.isWritable) {
      Try {
        key.attachment.asInstanceOf[Connector].write
      } match {
        case Failure(e) => {
          log.info("closing {}", key.attachment)
          key.cancel
          return
        }
        case _ =>
      }
    }
  }

  override def close(): Unit = {
    if(!closed.compareAndSet(false,true)) return
    Try {
      thread.interrupt
      thread.join(10*1000)
      if(thread.isAlive) throw new IOException("join timed out")
    } match {
      case Failure(e) => {
        log.error("fatal error closing ConnectionManager", e);
        System.exit(-1);
      }
      case _ =>
    }
  }

}
