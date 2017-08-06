package aug.gui

import aug.io._
import aug.profile.ProfileConfig
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

case class Fragment(text: String, colorCode: ColorCode) {
  def splitAt(index: Int) : (Fragment, Fragment) = {
    (Fragment(text.substring(0, index), colorCode), Fragment(text.substring(index), colorCode))
  }

  def highlight: Fragment = this.copy(colorCode = colorCode.copy(bg = TelnetColorBlue))
}

case class Line(fragments: List[Fragment], commands: List[String], lineNum: Long, pos: Int = 0) {
  def :+(fragment: Fragment): Line = {
    if (fragments.nonEmpty && fragments.last.colorCode == fragment.colorCode) {
      appendToLastFragment(fragment.text)
    } else Line(fragments :+ fragment, commands, lineNum, pos)
  }

  private def appendToLastFragment(text: String) = {
    val last = fragments.last
    Line(fragments.dropRight(1) :+ Fragment(last.text + text, last.colorCode), commands, lineNum, pos)
  }

  def mergeCommands(profileConfig: ProfileConfig): List[Line] = {
    if (profileConfig.consoleWindow.echoCommands && commands.nonEmpty) {
      val builder = List.newBuilder[Line]

      if (profileConfig.consoleWindow.cmdsOnNewLine) {
        builder += this

        if (profileConfig.consoleWindow.stackCmds) {
          val frag = Fragment(commands.mkString(" | "), ColorCode(TelnetColorYellow))
          builder += Line(List(frag), List.empty, lineNum, length)
        } else {
          commands.foreach { cmd =>
            builder += Line(List(Fragment(cmd, ColorCode(TelnetColorYellow))), List.empty, lineNum, length)
          }
        }
      } else {
        if (profileConfig.consoleWindow.stackCmds) {
          val frag = Fragment(commands.mkString(" | "), ColorCode(TelnetColorYellow))
          builder += this.copy(fragments = this.fragments :+ frag)
        } else {
          val xs :: tail = commands
          builder += this.copy(fragments = this.fragments :+ Fragment(xs, ColorCode(TelnetColorYellow)))
          tail.foreach { cmd =>
            builder += Line(List(Fragment(cmd, ColorCode(TelnetColorYellow))), List.empty, lineNum, length)
          }
        }
      }

      builder.result()
    } else List(this)
  }

  def length: Int = fragments.map(_.text.length).sum

  def split(wrapAt: Int) : List[Line] = {

    if (fragments.isEmpty) {
      return List[Line](this)
    }

    val lines = List.newBuilder[Line]
    val frags = List.newBuilder[Fragment]

    def addLine(fragment: Fragment, pos: Int): Int = {
      frags += fragment
      val newLine = Line(frags.result, List.empty, lineNum, pos)
      lines += newLine
      frags.clear
      pos + newLine.length
    }

    @tailrec
    def addFragments(fragments: List[Fragment], fragment: Fragment, pos: Int, room: Int = wrapAt) : Int = {
      if (fragment.text.length == room) {
        val newPos = addLine(fragment, pos)
        if (fragments != Nil) addFragments(fragments.tail, fragments.head, newPos) else pos
      } else if (fragment.text.length < room) {
        frags += fragment
        if (fragments != Nil) addFragments(fragments.tail, fragments.head, pos, room - fragment.text.length) else pos
      } else {
        val (head, tail) = fragment.splitAt(room)
        val newPos = addLine(head, pos)
        addFragments(fragments, tail, newPos)
      }
    }

    val pos = addFragments(fragments.tail, fragments.head, 0)

    val result = frags.result
    if(result.nonEmpty) {
      lines += Line(result, List.empty, lineNum, pos)
    }

    lines.result
  }

  def str: String = fragments.map(_.text).mkString

  def highlight(start: TextPos, end: TextPos): Line = {
    if (lineNum >= start.lineNum && lineNum <= end.lineNum) {
      var fragbuilder = List.newBuilder[Fragment]

      @tailrec
      def handleFragment(list: List[Fragment], pos: Int): Unit = {
        list match {
          case xs :: tail =>
            val fragStart = TextPos(lineNum, pos)
            // fragEnd is up to but not including, it's the starting pos of the next frag (if there is one)
            val fragEnd = TextPos(lineNum, xs.text.length + pos - 1)

            if (start <= fragEnd && start > fragStart) {
              val (keep, remainder) = xs.splitAt(start.pos - pos)
              fragbuilder += keep
              handleFragment(remainder :: tail, pos + keep.text.length)
            } else {
              if (start <= fragStart && fragEnd <= end) {
                fragbuilder += xs.highlight
              } else if (start > fragEnd || end < fragStart) {
                fragbuilder += xs
              } else {
                // end slices the fragment and start doesn't
                val (keep, remainder) = xs.splitAt(end.pos - pos + 1)
                fragbuilder += keep.highlight
                fragbuilder += remainder
              }

              handleFragment(tail, fragEnd.pos + 1)
            }

          case Nil =>
        }
      }

      handleFragment(fragments, pos)

      this.copy(fragments = fragbuilder.result())
    } else this
  }
}

object EmptyLine {
  def apply(lineNum: Long): Line = Line(List(Fragment("", DefaultColorCode)), List.empty, lineNum)
}

sealed trait TextState
case object TextStateStream extends TextState
case object TextStateColor extends TextState
case object TextStateEscape extends TextState

class Text(var profileConfig: ProfileConfig) {

  import Text.log
  private val lines = scala.collection.mutable.Map[Long, Line]()
  private var botLine : Long = 0

  lines(botLine) = EmptyLine(botLine)

  def apply(lineNum: Long): Line = lines.getOrElse(lineNum, EmptyLine(lineNum))

  def getWrapLines(numLines: Int, wrapAt: Int, botLine: Long): List[Line] = synchronized {
    val bl = if(botLine == -1) this.botLine else botLine

    @tailrec
    def get(numLines: Int, lineNum: Long, rv: List[Line] = List.empty): List[Line] = {
      if(numLines <= 0 || lineNum < 0) {
        rv
      } else {
        val toadd = lines.getOrElse(lineNum, EmptyLine(lineNum)).mergeCommands(profileConfig).flatMap(_.split(wrapAt))
        get(numLines - toadd.size, lineNum - 1, toadd ++ rv)
      }
    }

    val rv = get(numLines, bl)
    rv.drop(rv.length - numLines)
  }

  def length: Long = synchronized(botLine)

  def addCommand(lineNum: Long, cmd: String): Unit = synchronized {
    val line = lines(lineNum)
    lines(lineNum) = line.copy(commands = line.commands :+ cmd)
  }

  def addLine(txt: String): Unit = synchronized {
    setLine(botLine + 1, txt)
  }

  def setLine(lineNum: Long, txt: String): Unit = synchronized {
    if (txt.contains("\n")) throw new Exception("text should not contain newline")

    var colorCode : ColorCode = DefaultColorCode

    val color = StringBuilder.newBuilder
    val text = StringBuilder.newBuilder
    var state : TextState = TextStateStream

    val fragments = List.newBuilder[Fragment]

    def addFragment() : Unit = {
      if (text.nonEmpty) {
        fragments +=  Fragment(text.result(), colorCode)
        text.clear
      }
    }

    txt.getBytes.foreach { b =>
      state match {
        case TextStateStream =>
          if (b == 27.toByte) {
            addFragment()
            state = TextStateEscape
          } else if (b != '\r') {
            text += b.toChar
          }

        case TextStateColor =>
          if (b == 'm') {
            colorCode = setColor(color.result(), colorCode)
            color.clear
            state = TextStateStream
          } else {
            color += b.toChar
          }

        case TextStateEscape =>
          state = if (b == '[') {
            TextStateColor
          } else TextStateStream
      }
    }

    addFragment()

    if (lines.size > lineNum) {
      lines(lineNum) = lines(lineNum).copy(fragments = fragments.result())
    } else lines(lineNum) = Line(fragments.result(), List.empty, lineNum)

    botLine = Math.max(botLine, lineNum)
  }

  private def setColor(s: String, colorCode: ColorCode) : ColorCode = {

    if (s == "0") return DefaultColorCode

    Try {
      var fg = colorCode.fg
      var bg = colorCode.bg
      var bold = colorCode.bold

      s.split(";").map(_.toInt).foreach {
        case 0 => bold = false
        case 1 => bold = true

        case 2 => // draw feintly -- won't support yet
        case 4 => // draw underline -- won't support yet
        case 7 => // reverse fg/bg -- won't support yet

        case 30 => fg = TelnetColorBlack
        case 31 => fg = TelnetColorRed
        case 32 => fg = TelnetColorGreen
        case 33 => fg = TelnetColorYellow
        case 34 => fg = TelnetColorBlue
        case 35 => fg = TelnetColorMagenta
        case 36 => fg = TelnetColorCyan
        case 37 => fg = TelnetColorWhite

        case 39 => fg = TelnetColorDefaultFg

        case 40 => bg = TelnetColorBlack
        case 41 => bg = TelnetColorRed
        case 42 => bg = TelnetColorGreen
        case 43 => bg = TelnetColorYellow
        case 44 => bg = TelnetColorBlue
        case 45 => bg = TelnetColorMagenta
        case 46 => bg = TelnetColorCyan
        case 47 => bg = TelnetColorWhite

        case 49 => bg = TelnetColorDefaultBg
      }

      ColorCode(fg, bg, bold)
    } match {
      case Success(cc) => cc
      case Failure(e) =>
        log.error(s"error parsing color code $s", e)
        DefaultColorCode
    }
  }

  def clear(): Unit = synchronized {
    lines.clear()
  }
}

object Text {
  val log = Logger(LoggerFactory.getLogger(Text.getClass))
}

case class TextPos(lineNum: Long, pos: Int) extends Comparable[TextPos] {
  def <(o: TextPos): Boolean = this.compareTo(o) < 0
  def >(o: TextPos): Boolean = this.compareTo(o) > 0
  def <=(o: TextPos): Boolean = this.compareTo(o) <= 0
  def >=(o: TextPos): Boolean = this.compareTo(o) >= 0

  override def compareTo(o: TextPos): Int = {
    if (lineNum > o.lineNum) {
      1
    } else if (lineNum < o.lineNum) {
      -1
    } else {
      pos - o.pos
    }
  }
}