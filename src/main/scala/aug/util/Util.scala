package aug.util

import java.awt.image.BufferedImage
import java.awt.{Color, Font, GraphicsEnvironment}
import java.io._
import java.nio.ByteBuffer
import java.util.concurrent.{Executors, Future}
import java.util.regex.Pattern

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

object TryWith {
  def apply[C <: Closeable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(resourceInstance => {
      try {
        val returnValue = f(resourceInstance)
        Try(resourceInstance.close()).map(_ => returnValue)
      }  catch {
        case NonFatal(exceptionInFunction) =>
          try {
            resourceInstance.close()
            Failure(exceptionInFunction)
          } catch {
            case NonFatal(exceptionInClose) =>
              exceptionInFunction.addSuppressed(exceptionInClose)
              Failure(exceptionInFunction)
          }
      }
    })
}

class RingBuffer[A](val capacity: Int)(implicit m: ClassTag[A]) extends scala.collection.mutable.IndexedSeq[A] {
  private val data: Array[A] = new Array[A](capacity)
  private var index = 0
  var length = 0

  private def off(idx: Int) : Int = (index -idx+capacity) % capacity

  def push(elem: A) : Unit = {
    if(length < capacity) length += 1
    index = (1+index)%length
    data(index) = elem
  }

  def apply(idx: Int) : A = {
    if(idx < 0 || idx >= capacity) throw new IndexOutOfBoundsException
    data(off(idx))
  }

  override def update(idx: Int, elem: A): Unit = {
    if(idx < 0 || idx >= length) throw new IndexOutOfBoundsException
    data(off(idx)) = elem
  }
}

object Util {

  private val tp = Executors.newCachedThreadPool()

  def time[T](f: => T): (Long, T) = {
    val ms = System.currentTimeMillis()
    val rv = f
    (System.currentTimeMillis() - ms, rv)
  }

  def printTime[T](f: => T): T = {
    val (ms,t) = time(f)
    println(s"took $ms")
    t
  }

  def run[T](f: => T): Future[T] = tp.submit(() => f)

  def invokeLater(f: () => Unit): Future[_] = {
    tp.submit(new Runnable { def run(): Unit = f() })
  }

  def invokeLater(timeout: Long, f: () => Unit): Future[_] = {
    tp.submit(new Runnable {
      override def run(): Unit = {
        Try {
          Thread.sleep(timeout)
        }

        f()
      }
    })
  }

  val log = Logger(LoggerFactory.getLogger(Util.getClass))

  val name = "August MC"
  val major = 2017
  val minor = 3

  def fullName : String = s"$name $version"
  def version : String = s"$major.$minor"

  def concatenate(args: Array[Byte]*) : Array[Byte] = {
    val length = (args map {
      _.length
    }).sum
    val bb = ByteBuffer.allocate(length)

    for(b <- args) bb.put(b)
    bb.array
  }

  def right(bytes: Array[Byte], length: Int): Array[Byte] = {
    val bb = ByteBuffer.allocate(length)
    bb.put(bytes,0,length)
    bb.array()
  }

  def toHex(color: Color): String = f"#${color.getRed}%02x${color.getGreen}%02x${color.getBlue}%02x".toUpperCase

  val fontSizes = Array(8, 9, 10, 11, 12, 13, 14, 18, 24, 36, 48, 64)

  val monospaceFamilies: immutable.Seq[String] = {
    // create a graphics environment to measure fonts
    val bf = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB)
    val bfg = bf.createGraphics
    val letters: IndexedSeq[Char] = (for (a <- 'a' to 'z') yield a) ++ (for (a <- 'A' to 'Z') yield a)

    GraphicsEnvironment.getLocalGraphicsEnvironment.getAllFonts.filter { font =>
      val fm = bfg.getFontMetrics(font)
      val widths = letters.map(ch => fm.stringWidth("" + ch)).toSet
      !letters.exists(!font.canDisplay(_)) && widths.size == 1
    }.map(_.getFamily()).toSet.toList.sorted
  }

  val defaultFont: Font = {
    val desirableFonts = List("Menlo", "Consolas")

    desirableFonts.find(monospaceFamilies.contains).map(new Font(_, 0, 12))
      .getOrElse(new Font(Font.MONOSPACED, 0, 12))
  }
}