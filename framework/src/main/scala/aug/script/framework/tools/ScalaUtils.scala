package aug.script.framework.tools

import aug.script.framework.reload.ReloadException

import scala.annotation.tailrec
import scala.collection.mutable

object ScalaUtils {

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
