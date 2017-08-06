package aug.script

import java.lang
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import aug.profile.{ClientEvent, Profile}
import aug.script.framework.tools.ScalaUtils
import aug.script.framework.{FutureEvent, RunnableReloader, SchedulerInterface}
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.mutable

trait ClientCaller {
  def callOnClient(): Unit
}

class Scheduler(client: Client, profile: Profile, reloaders: Seq[RunnableReloader[_ <: Runnable]])
  extends SchedulerInterface with AutoCloseable {

  private val log = Scheduler.log
  private val events = mutable.Set[Event]()
  private val executor = Executors.newScheduledThreadPool(1)
  private val reloaderMap: Map[String, RunnableReloader[_]] = reloaders.map(r => r.runnableType().getCanonicalName -> r).toMap

  case class Event(timeout: Long, runnable: Runnable, periodic: Boolean = false) extends Runnable
    with ClientCaller with FutureEvent {

    if (!periodic) events.add(this)

    private val cancellable = new AtomicBoolean(true)

    override def run(): Unit = {
      if (periodic && !cancellable.get) {
        // the scheduled task will stop executing.
        throw new RuntimeException("")
      }

      profile.offer(ClientEvent(this))
    }

    override def callOnClient(): Unit = {
      if (!periodic)  events.remove(this)

      if (periodic || cancellable.compareAndSet(true, false)) {
        try {
          client.handleEvent(runnable)
        } catch {
          case rej: RejectedExecutionException => // client is stale
          case e: Throwable => throw e
        }
      }
    }

    def saveable: Boolean = cancellable.get

    override def cancel(): lang.Boolean = {
      cancellable.compareAndSet(true, false)
    }
  }

  override def in(timeout: Long, runnable: Runnable): FutureEvent = {
    val event = Event(System.currentTimeMillis() + timeout, runnable)
    executor.schedule(event, timeout, TimeUnit.MILLISECONDS)
    event
  }

  def save: List[String] = events.toList.filter(_.saveable).flatMap(saveEvent)

  private def saveEvent(event: Event): Option[String] = {
    val cn = event.runnable.getClass.getCanonicalName
    reloaderMap.get(cn).flatMap { reloader =>
      try {
        val runString = reloader.convertToString(event.runnable)
        Some(ScalaUtils.encodeArgs(cn, event.timeout.toString, runString))
      } catch {
        case e: Throwable =>
          profile.slog.error("exception serializing runnable to string", e)
          None
      }
    }
  }

  def hydrate(strings: List[String]): Unit = {
    strings.foreach { string =>
      val list = ScalaUtils.decodeList(string)
      reloaderMap.get(list.head).foreach { reloader =>
        val scheduledTime = list(1).toLong
        val delay = Math.max(0, scheduledTime - System.currentTimeMillis())
        try {
          val run: Runnable = reloader.stringToRunnable(list.last)
          executor.schedule(Event(scheduledTime, run), delay, TimeUnit.MILLISECONDS)
        } catch {
          case e: Throwable =>
            profile.slog.error("exception hydrating runnable from string", e)
        }
      }
    }
  }

  override def close(): Unit = {
    executor.shutdownNow()
    if (!executor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
      log.error("failed to shutdown client executor service")
    }
  }

  override def every(initialDelay: Long, period: Long, runnable: Runnable): FutureEvent = {
    val event = Event(0, runnable, periodic = true)
    executor.scheduleAtFixedRate(event, initialDelay, period, TimeUnit.MILLISECONDS)
    event
  }
}

object Scheduler {
  val log = Logger(LoggerFactory.getLogger(Scheduler.getClass))
}