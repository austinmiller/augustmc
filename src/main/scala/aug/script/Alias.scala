package aug.script

import java.util.regex.{Matcher, Pattern}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Alias {

  private class Alias(val patternString: String,val callback: Matcher => Unit) {
    val pattern = Pattern.compile(patternString)

    def handle(line: String): Boolean = {
      val matcher = pattern.matcher(line)
      if(matcher.matches) {
        Game.consumeNextCommand()
        callback(matcher)
        true
      } else false
    }
  }

  private val aliases = mutable.Set[Alias]()

  def processAliases(command: String) : Unit = {
    aliases foreach {a=>
      val r = Try {
        a.handle(command)
      } match {
        case Failure(e) =>
          Game.handleException(e)
          false
        case Success(v) => v
      }

      if(r) return
    }
  }

  def alias(pattern: String, callback: Matcher => Unit) = aliases.add(new Alias("^"+pattern+"$",callback))
  def simpleAlias(pattern: String, callback: Matcher => Unit) = aliases.add(new Alias("^"+Pattern.quote(pattern)+"$",callback))
}



object Trigger {
  def processFragmentTriggers(noColors: String): Unit = {}

  def processTriggers(line: String) = {

  }
}

class Trigger {

}