package aug.io

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels._
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.util.{Failure, Try}

trait Connector {
  def onConnect(): Unit
  def onDisconnect(): Unit
  def isConnected(): Boolean
  def onCancel(): Unit
  def read(): Unit
  def write(): Unit
  def address: InetSocketAddress
  def setSocketChannel(channel: SocketChannel) : Unit
  def error(msg: String) : Unit
}

object Connector {
  val log = Logger(LoggerFactory.getLogger(ConnectionManager.getClass))
}

abstract class AbstractConnection(val address: InetSocketAddress) extends Connector with AutoCloseable {

  private val log = Connector.log
  private var channel : SocketChannel = _
  private val connected = new AtomicBoolean(false)
  private val closed = new AtomicBoolean(false)
  private val in = ByteBuffer.allocate(2<<20)
  private val queue = new ConcurrentLinkedQueue[Array[Byte]]()
  private var out : Option[ByteBuffer] = None

  def isClosed: Boolean = closed.get
  override def isConnected: Boolean = channel.isConnected

  override def close() : Unit = {
    if(!closed.compareAndSet(false, true)) return

    queue.clear()
    Try {
      if(channel != null) channel.close()
    } match {
      case Failure(e) => log.warn("failed to close socket",e)
      case _ =>
    }
    log.info("closed socket")
  }

  def connect(): Unit = {
    Try {
      ConnectionManager.register(this)
      connected.set(true)
    } match {
      case Failure(e) =>
        log.error("error registering socket",e)
        error(e.getMessage)
        close()
      case _ =>
    }
  }

  override def onConnect() : Unit = {
    Try {
      log.info("already connected? {}", channel.isConnected)

      if (isClosed) {
        throw new IOException("closed")
      }

      channel.finishConnect()

      if (!channel.isOpen || !channel.isConnected) {
        throw new IOException("channel is not open or connected")
      }

      connected.set(true)
    } match {
      case Failure(e) =>
        log.error("failed to finish connection",e)
        error(e.getMessage)
        close()
      case _ =>
    }
  }

  def send(msg : Array[Byte]) {
    if (!isClosed) {
      queue.add(msg)
    }
  }

  def send(s: String) : Unit = send(s.getBytes())

  override def setSocketChannel(channel: SocketChannel): Unit = this.channel = channel

  protected def handleIncoming(bytes: Array[Byte]) : Unit

  override def read() : Unit = {
    Try {
      in.clear

      val read = channel.read(in)

      if (read < 0) throw new IOException("connection reset by peer")

      in.flip

      if (!in.hasRemaining) return

      val copy = new Array[Byte](in.limit)
      System.arraycopy(in.array(), 0, copy, 0, copy.length)
      handleIncoming(copy)
    } match {
      case Failure(e: IOException) =>
        log.info("connection reset by peer")
        onDisconnect()
        close()

      case Failure(e) =>
        log.error("error on read, closing", e)
        onDisconnect()
        close()

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
      case Failure(e) =>
        log.error("error on write, closing", e)
        onDisconnect()
        close()

      case _ =>
    }
  }

  override def onDisconnect(): Unit = {
    connected.set(false)
  }

  override def onCancel(): Unit = {
    if (connected.compareAndSet(true, false)) {
      onDisconnect()
    }

    if (!isClosed) {
      close()
    }
  }

}

trait Server {
  def accept() : Unit
  def setServerSocketChannel(channel: ServerSocketChannel)
  def address : InetSocketAddress
}


