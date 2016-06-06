package aug.util

import java.io.{Closeable, File, FileOutputStream}
import java.nio.ByteBuffer

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

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

object Util {

  val log = Logger(LoggerFactory.getLogger(Util.getClass))

  val name = "August MC"
  val major = 2016
  val minor = 1

  def fullName : String = s"$name $version"
  def version : String = s"$major.$minor"


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
