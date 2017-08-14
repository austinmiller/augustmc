package aug.script.framework.tools

import java.io.{PrintWriter, StringWriter}
import java.util.regex.Pattern

import aug.script.framework.reload.ReloadException

import scala.annotation.tailrec
import scala.collection.mutable

object ScalaUtils {

  def matchColor(codes: Int*): String = Pattern.quote("" + 27.toByte.toChar + "[" + codes.mkString(";") + "m")

  def toString(throwable: Throwable): String = {
    val sw = new StringWriter
    val pw = new PrintWriter(sw, true)
    throwable.printStackTrace(pw)
    sw.getBuffer.toString
  }

  def encodeColor(code: String): String = "" + 27.toByte.toChar + "[" + code + "m"

  def removeColors(string: String): String = string.replaceAll("\u001B\\[.*?m", "")

  def encodeArgs(strings: String*): String = encodeIterable(strings)

  def encodeArray(array: Array[String]): String = encodeIterable(array)
  def decodeArray(string: String): Array[String] = decodeIterable(string, List.newBuilder[String]).toArray[String]

  def encodeList(list: List[String]): String = encodeIterable(list)
  def decodeList(string: String): List[String] = decodeIterable(string, List.newBuilder[String])

  def encodeSet(set: Set[String]): String = encodeIterable(set.toList)
  def decodeSet(string: String): Set[String] = decodeIterable(string, Set.newBuilder[String])

  private def decodeIterable[A <: Iterable[String]](string: String, builder: mutable.Builder[String, A]): A = {

    @tailrec
    def process(string: String): Unit = {
      if (string.nonEmpty) {
        val tokens = string.split(":", 2)
        if (tokens.length != 2) throw new ReloadException("failure decoding collection")
        val len = tokens(0).toInt
        val (head, tail) = tokens(1).splitAt(len)
        builder += head
        process(tail)
      }
    }

    process(string)

    builder.result()
  }

  private def encodeIterable(seq: Iterable[String]): String = seq.map(s=> s"${s.length}:$s").mkString("")
}
