package aug.io

import java.io.{File, FileOutputStream}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit}

import aug.gui.{TextState, TextStateColor, TextStateStream}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

object TextLogger {
  val log = Logger(LoggerFactory.getLogger(TextLogger.getClass))
}

class TextLogger(dir: File) extends AutoCloseable {
  import TextLogger.log

  private val logFile = {
    if (!dir.exists()) throw new Exception("text log doesn't exist")
    if (!dir.isDirectory) throw new Exception(s"$dir isn't a directory")

    val simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd_kk.mm.ss")

    val dateString = simpleDateFormat.format(new Date())

    new File(dir, s"$prefix.$dateString.log")
  }

  private val out = new FileOutputStream(logFile)
  private val executorService = Executors.newFixedThreadPool(1)
  protected val textQueue = new LinkedBlockingQueue[String]()

  executorService.submit(new Runnable {
    override def run(): Unit = {
      while(!Thread.interrupted()) {
        try {
          out.write(textQueue.take().getBytes())
          out.flush()
        } catch {
          case i: InterruptedException =>
            log.info("interrupted, stopping logging")
            out.flush()
            return
          case e: Throwable =>
            log.error("error while logging", e)
        }
      }
    }
  })

  protected def prefix = "colorLog"

  def addText(text: String): Unit = {
    textQueue.offer(text)
  }

  override def close(): Unit = {
    executorService.shutdownNow()
    if(!executorService.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
      log.error("failed to shutdown text logger service")
    }
    out.close()
  }
}

class ColorlessTextLogger(dir: File) extends TextLogger(dir) {

  override def prefix = "textLog"

  private var textState : TextState = TextStateStream

  override def addText(text: String): Unit = {
    val stringBuilder = StringBuilder.newBuilder

    text.getBytes.foreach { b =>
      textState match {
        case TextStateStream =>
          if (b == 27.toByte) {
            textState = TextStateColor
          } else {
            stringBuilder += b.toChar
          }

        case TextStateColor =>
          if (b == 'm') {
            textState = TextStateStream
          }

        case _ =>
      }
    }

    textQueue.offer(stringBuilder.result())
  }
}


