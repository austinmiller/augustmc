package aug.util

import java.awt.EventQueue
import java.io.{Closeable, File, FileOutputStream}
import java.nio.ByteBuffer
import java.util.concurrent.Executors

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.{Failure, Try}
import scala.util.control.NonFatal


object JsonUtil {
  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def toJson(value: Map[Symbol, Any]): String = {
    toJson(value map { case (k,v) => k.name -> v})
  }

  def toJson(value: Any): String = {
    mapper.writeValueAsString(value)
  }

  def toMap[V](json:String)(implicit m: Manifest[V]) = fromJson[Map[String,V]](json)

  def fromJson[T](json: String)(implicit m : Manifest[T]): T = {
    mapper.readValue[T](json)
  }
}

object TryWith {
  def apply[C <: Closeable, R](resource: => C)(f: C => R): Try[R] =
    Try(resource).flatMap(resourceInstance => {
      try {
        val returnValue = f(resourceInstance)
        Try(resourceInstance.close).map(_ => returnValue)
      }  catch {
        case NonFatal(exceptionInFunction) =>
          try {
            resourceInstance.close
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

  val tp = Executors.newFixedThreadPool(1)

  def invokeLater(f: () => Unit) = {
    tp.submit(new Runnable() { def run = f()})
  }

  val log = Logger(LoggerFactory.getLogger(Util.getClass))

  val name = "August MC"
  val major = 2016
  val minor = 1

  def fullName : String = s"$name $version"
  def version : String = s"$major.$minor"

  def isWindows : Boolean = System.getProperty("os.name").toLowerCase.contains("windows")


  def concatenate(args: Array[Byte]*) : Array[Byte] = {
    val length = args map { _.length } reduce { _ + _ }
    val bb = ByteBuffer.allocate(length)

    for(b <- args) bb.put(b)
    bb.array
  }

  def right(bytes: Array[Byte], length: Int) = {
    val bb = ByteBuffer.allocate(length)
    bb.put(bytes,0,length)
    bb.array()
  }

  def touch(file: File) : Unit = {
    Try {
      if(!file.exists) new FileOutputStream(file).close
      file.setLastModified(System.currentTimeMillis)
    } match {
      case Failure(e) => log.error(s"failed to touch file ${file.getAbsolutePath}",e)
      case _ =>
    }
  }

  def removeColors(string: String): String = string.replaceAll("\u001B\\[.*?m", "")

}


import scala.util.Random

object LoremIpsum {
  private val standard = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
  private val lipsumwords = Array(
    "a", "ac", "accumsan", "ad", "adipiscing", "aenean", "aliquam", "aliquet",
    "amet", "ante", "aptent", "arcu", "at", "auctor", "augue", "bibendum",
    "blandit", "class", "commodo", "condimentum", "congue", "consectetur",
    "consequat", "conubia", "convallis", "cras", "cubilia", "cum", "curabitur",
    "curae", "cursus", "dapibus", "diam", "dictum", "dictumst", "dignissim",
    "dis", "dolor", "donec", "dui", "duis", "egestas", "eget", "eleifend",
    "elementum", "elit", "enim", "erat", "eros", "est", "et", "etiam", "eu",
    "euismod", "facilisi", "facilisis", "fames", "faucibus", "felis",
    "fermentum", "feugiat", "fringilla", "fusce", "gravida", "habitant",
    "habitasse", "hac", "hendrerit", "himenaeos", "iaculis", "id", "imperdiet",
    "in", "inceptos", "integer", "interdum", "ipsum", "justo", "lacinia",
    "lacus", "laoreet", "lectus", "leo", "libero", "ligula", "litora",
    "lobortis", "lorem", "luctus", "maecenas", "magna", "magnis", "malesuada",
    "massa", "mattis", "mauris", "metus", "mi", "molestie", "mollis", "montes",
    "morbi", "mus", "nam", "nascetur", "natoque", "nec", "neque", "netus",
    "nibh", "nisi", "nisl", "non", "nostra", "nulla", "nullam", "nunc", "odio",
    "orci", "ornare", "parturient","pellentesque", "penatibus", "per",
    "pharetra", "phasellus", "placerat", "platea", "porta", "porttitor",
    "posuere", "potenti", "praesent", "pretium", "primis", "proin", "pulvinar",
    "purus", "quam", "quis", "quisque", "rhoncus", "ridiculus", "risus",
    "rutrum", "sagittis", "sapien", "scelerisque", "sed", "sem", "semper",
    "senectus", "sit", "sociis", "sociosqu", "sodales", "sollicitudin",
    "suscipit", "suspendisse", "taciti", "tellus", "tempor", "tempus",
    "tincidunt", "torquent", "tortor", "tristique", "turpis", "ullamcorper",
    "ultrices", "ultricies", "urna", "ut", "varius", "vehicula", "vel", "velit",
    "venenatis", "vestibulum", "vitae", "vivamus", "viverra", "volutpat",
    "vulputate")
  private val punctuation = Array(".", "?")
  private val _n = System.getProperty("line.separator")
  private val random = new Random

  def randomWord:String = lipsumwords(random.nextInt(lipsumwords.length - 1))

  def randomPunctuation:String = punctuation(random.nextInt(punctuation.length - 1))

  def words(count:Int):String =
    if (count > 0) (randomWord + " " + words(count - 1)).trim() else ""

  def sentenceFragment:String = words(random.nextInt(10) + 3)

  def sentence:String = {
    var s = new StringBuilder(randomWord.capitalize).append(" ")
    if (random.nextBoolean) {
      (0 to random.nextInt(3)).foreach({
        s.append(sentenceFragment).append(", ")
      })
    }
    s.append(sentenceFragment).append(randomPunctuation).toString
  }

  def sentences(count:Int):String =
    if (count > 0) (sentence + "  " + sentences(count - 1)).trim() else ""

  def paragraph(useStandard:Boolean = false) =
    if (useStandard) standard else sentences(random.nextInt(3) + 2)

  def paragraph:String = paragraph(false)

  def paragraphs(count: Int, useStandard:Boolean = false):String =
    if (count > 0) (paragraph(useStandard) + _n + _n + paragraphs(count - 1)).trim() else ""

}