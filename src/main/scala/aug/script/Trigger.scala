package aug.script

import java.util.regex.{Matcher, Pattern}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Trigger {

  val triggers = mutable.Set[Trigger]()

  def processFragmentTriggers(noColors: String): Unit = {}

  def processTriggers(line: String) : Unit = synchronized {
    if(line.length < 2) return

    val l = line.substring(0,line.length-1).replaceAll("\n","")
    triggers.foreach(t=>
      Try {
        t.handle(l)
      } match {
        case Failure(e) => Game.handleException(e)
        case Success(b) =>
      }
    )
  }

  def newSimpleTrigger(pattern: String, send: String): Trigger = {
    newTrigger(".*" + Pattern.quote(pattern) + ".*", (m: Matcher) => Game.send(send))
  }

  def newTrigger(pattern: String, callback: Matcher => Unit): Trigger = {
    register(new Trigger("^" + pattern + "$",callback))
  }

  def register(trigger: Trigger) : Trigger = synchronized {
    triggers.add(trigger)
    trigger
  }

  def unregister(trigger: Trigger) : Unit = synchronized { triggers.remove(trigger) }

}

class Trigger(patternString: String, val callback: Matcher => Unit,var enabled: Boolean = true) {
  val pattern = Pattern.compile(patternString)

  def handle(line: String): Boolean = {
    if(!enabled) false else {
      val matcher = pattern.matcher(line)
      if (matcher.matches) {
        Game.consumeNextCommand()
        callback(matcher)
        true
      } else false
    }
  }
}

class OneTimeTrigger(patternString: String, val callback: Matcher => Unit) extends Trigger(patternString,callback) {
  override def handle(line: String): Boolean = {
    if(super.handle(line)) {
      Trigger.unregister(this)
      true
    } else false
  }
}
