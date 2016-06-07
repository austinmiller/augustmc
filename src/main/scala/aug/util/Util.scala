package aug.util

import java.io.{Closeable, File, FileOutputStream}
import java.nio.ByteBuffer

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag
import scala.util.{Failure, Try}
import scala.util.control.NonFatal


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
}
