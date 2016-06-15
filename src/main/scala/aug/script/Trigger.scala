package aug.script

import java.util.regex.{Matcher, Pattern}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Trigger {

  val triggers = mutable.Set[Trigger]()
  val fragmentTriggers = mutable.Set[Trigger]()

  def processFragmentTriggers(frag: String): Unit = {
    if(frag.length <2) return
    val l = frag.replaceAll("\n","")
    fragmentTriggers.filter(_.enabled).foreach{t=>
      Try {
        t.handle(l)
      } match {
        case Failure(e) => Game.handleException(e)
        case Success(b) =>
      }
    }
  }

  def processTriggers(line: String) : Unit = synchronized {
    if(line.length < 2) return

    val l = line.substring(0,line.length-1).replaceAll("\n","")
    triggers.filter(_.enabled).foreach(t=>
      Try {
        t.handle(l)
      } match {
        case Failure(e) => Game.handleException(e)
        case Success(b) =>
      }
    )
  }

  def oneTimeTrigger(pattern: String, callback: Matcher => Unit): Trigger = {
    register(new OneTimeTrigger("^" + pattern + "$",callback))
  }

  def simpleTrigger(pattern: String, send: String): Trigger = {
    trigger(".*" + Pattern.quote(pattern) + ".*", (m: Matcher) => Game.send(send))
  }

  def trigger(pattern: String, callback: Matcher => Unit, enabled: Boolean = true, triggerOptions: TriggerOptions = TriggerOptions()): Trigger = {
    register(new Trigger("^" + pattern + "$",callback,enabled,triggerOptions))
  }

  def register(trigger: Trigger) : Trigger = synchronized {
    if(trigger.triggerOptions.fragmentTrigger) fragmentTriggers.add(trigger) else triggers.add(trigger)
    trigger
  }
  def unregister(trigger: Trigger) : Unit = synchronized {
    triggers.remove(trigger)
    fragmentTriggers.remove(trigger)
  }

}

case class TriggerOptions(fireOnce: Boolean=false, fragmentTrigger: Boolean =false, oneTime: Boolean = false)

class Trigger(patternString: String, val callback: Matcher => Unit,var enabled: Boolean = true, val triggerOptions: TriggerOptions = TriggerOptions()) {
  val pattern = Pattern.compile(patternString)

  def handle(line: String): Boolean = {
    if(!enabled) false else {
      val matcher = pattern.matcher(line)
      if (matcher.matches) {
        Game.consumeNextCommand()
        callback(matcher)
        if(triggerOptions.fireOnce) enabled = false
        if(triggerOptions.oneTime) Trigger.unregister(this)
        true
      } else false
    }
  }
}

class OneTimeTrigger(patternString: String, override val callback: Matcher => Unit) extends Trigger(patternString,callback,true,TriggerOptions()) {
  override def handle(line: String): Boolean = {
    if(super.handle(line)) {
      Trigger.unregister(this)
      true
    } else false
  }
}
