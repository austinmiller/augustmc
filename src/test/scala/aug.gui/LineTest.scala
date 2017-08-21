package aug.gui

import aug.gui.text.{Fragment, Line, TextPos}
import aug.io.{DefaultColorCode, TelnetColorBlue, TelnetColorDefaultBg}
import org.scalatest.FlatSpec

class LineTest extends FlatSpec {

  "a line" should "highlight a fragment" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 0)

    val hline = line.highlight(TextPos(0, 0), TextPos(0, 3))

    assert(hline.fragments.size == 1)
    assert(hline.fragments.head.colorCode.bg == TelnetColorBlue)
  }

  "a line" should "not highlight a fragment" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10)

    val hline = line.highlight(TextPos(0, 0), TextPos(0, 3))

    assert(hline.fragments.size == 1)
    assert(hline.fragments.head.colorCode.bg == TelnetColorDefaultBg)
  }

  "a very wide textpos" should "highlight a fragment" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10)

    val hline = line.highlight(TextPos(0, 0), TextPos(400, 3))

    assert(hline.fragments.size == 1)
    assert(hline.fragments.head.colorCode.bg == TelnetColorBlue)
  }

  "a partial textpos" should "slice a fragment in two" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10)

    val hline = line.highlight(TextPos(10, 2), TextPos(400, 3))

    assert(hline.fragments.size == 2)
    assert(hline.fragments.head.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments.last.colorCode.bg == TelnetColorBlue)
    assert(hline.fragments.head.text == "fo")
    assert(hline.fragments.last.text == "ur")
  }

  "a partial textpos" should "slice a fragment in two from behind" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10)

    val hline = line.highlight(TextPos(0, 0), TextPos(10, 1))

    assert(hline.fragments.size == 2)
    assert(hline.fragments.head.colorCode.bg == TelnetColorBlue)
    assert(hline.fragments.last.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments.head.text == "fo")
    assert(hline.fragments.last.text == "ur")
  }

  "a partial textpos" should "slice a fragment from both sides" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10)

    val hline = line.highlight(TextPos(10, 1), TextPos(10, 2))

    assert(hline.fragments.size == 3)
    assert(hline.fragments.head.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments(1).colorCode.bg == TelnetColorBlue)
    assert(hline.fragments.last.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments.head.text == "f")
    assert(hline.fragments(1).text == "ou")
    assert(hline.fragments.last.text == "r")
  }

  "a line" should "highlight a word-wrapped fragment" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 0, 80)

    val hline = line.highlight(TextPos(0, 80), TextPos(0, 85))

    assert(hline.fragments.size == 1)
    assert(hline.fragments.head.colorCode.bg == TelnetColorBlue)
  }

  "a partial textpos" should "slice a word-wrapped fragment from both sides" in {
    val line = Line(List(
      Fragment("four", DefaultColorCode)
    ), List.empty, 10, 80)

    val hline = line.highlight(TextPos(10, 81), TextPos(10, 82))

    assert(hline.fragments.size == 3)
    assert(hline.fragments.head.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments(1).colorCode.bg == TelnetColorBlue)
    assert(hline.fragments.last.colorCode.bg == TelnetColorDefaultBg)
    assert(hline.fragments.head.text == "f")
    assert(hline.fragments(1).text == "ou")
    assert(hline.fragments.last.text == "r")
  }
}
