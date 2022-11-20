package aug.io

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel, SocketChannel}
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{Failure, Try}

import scala.jdk.CollectionConverters._

case class Attachment(connector: Connector, channel: SocketChannel)

object ConnectionManager extends AutoCloseable with Runnable  {
  val log: Logger = Logger(LoggerFactory.getLogger(ConnectionManager.getClass))

  private val closed = new AtomicBoolean(false)
  private val selector : Selector = Selector.open
  private val thread	= new Thread(this, "TelnetManager")
  private val readWriteMask: Int = SelectionKey.OP_READ | SelectionKey.OP_WRITE

  private def configure(channel: SocketChannel): Unit = {
    channel.configureBlocking(false)
    channel.socket().setSendBufferSize(0x100000) // 1Mb
    channel.socket().setReceiveBufferSize(0x100000) // 1Mb
    channel.socket().setKeepAlive(true)
    channel.socket().setReuseAddress(true)
    channel.socket().setSoLinger(false, 0)
    channel.socket().setSoTimeout(0)
    channel.socket().setTcpNoDelay(true)
  }

  def register(connector: Connector) : Unit = {
    val channel = SocketChannel.open()
    configure(channel)

    connector.setSocketChannel(channel)

    if (channel.connect(connector.address)) {
      onConnect(connector, channel)
    } else {
      channel.register(selector, SelectionKey.OP_CONNECT, Attachment(connector, channel))
    }

    if (log.underlying.isDebugEnabled) {
      selector.keys.asScala.foreach { sk => log.debug("class={}", sk.attachment.getClass.getCanonicalName) }
    }

    log.debug("connector registered: {}", channel)
  }

  private def onConnect(connector: Connector, channel: SocketChannel): Unit = {
    log.info(s"connected to ${channel.getRemoteAddress}")
    connector.onConnect()
    channel.register(selector, readWriteMask, Attachment(connector, channel))
  }

  def register(server: Server): Unit = {
    val channel = ServerSocketChannel.open()
    channel.configureBlocking(false)
    channel.socket().setReuseAddress(true)
    channel.socket.setSoTimeout(0)
    channel.socket.bind(server.address)

    server.setServerSocketChannel(channel)

    channel.register(selector, SelectionKey.OP_ACCEPT, server)

    log.debug("server registered: {}", server)
  }

  def start() : Unit = {
    log.info("starting")
    thread.start()
  }

  override def run() : Unit = {
    while (!closed.get && selector.isOpen) {
      try {
        selector.selectNow
        processKeys()
        Thread.sleep(50)
      } catch {
        case e: InterruptedException => log.info("thread interrupted")
        case e: Throwable => log.error("exception caught during selection", e)
      }
    }
    selector.close()
  }

  private def processKeys(): Unit = selector.selectedKeys.asScala.foreach(processKey)

  private def connect(key: SelectionKey, connector: Connector, channel: SocketChannel): Unit = {
    try {
      if (!channel.isConnected && channel.finishConnect()) {
        onConnect(connector, channel)
      }
    } catch {
      case e: Throwable =>
        log.error(s"error trying to connect to ${channel.getRemoteAddress}", e)
        key.cancel()
        connector.onDisconnect()
    }
  }

  private def processClient(key: SelectionKey, connector: Connector, channel: SocketChannel): Unit = {
    if (!key.isValid) {
      connector.onCancel()
      key.cancel()
    } else {
      if (key.isConnectable) {
        connect(key, connector, channel)
      }

      if (key.isValid && key.isReadable) {
        read(key, connector)
      }

      if (key.isValid && key.isWritable) {
        write(key, connector)
      }
    }
  }

  private def write(key: SelectionKey, connector: Connector): Unit = {
    Try {
      connector.write()
    } match {
      case Failure(e) =>
        log.info("closing {}", key.attachment)
        key.cancel()

      case _ =>
    }
  }

  private def read(key: SelectionKey, connector: Connector): Unit = {
    Try {
      connector.read()
    } match {
      case Failure(e) =>
        log.info("closing {}", key.attachment)
        key.cancel()

      case _ =>
    }
  }

  private def processServer(key: SelectionKey, server: Server) = {
    if (key.isAcceptable) {
      server.accept()
    }
  }

  private def processKey(key : SelectionKey): Unit = {
    key.attachment() match {
      case Attachment(connector, channel) => processClient(key, connector, channel)
      case server: Server => processServer(key, server)
    }
  }

  override def close(): Unit = {
    if (closed.compareAndSet(false,true)) {
      Try {
        thread.interrupt()
        thread.join(10 * 1000)
        if (thread.isAlive) throw new IOException("join timed out")
      } match {
        case Failure(e) =>
          log.error("fatal error closing ConnectionManager", e)
          System.exit(-1)

        case _ =>
      }
    }
  }
}
