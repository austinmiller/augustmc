package aug.script.framework.reload

import java.lang.Long
import java.util
import javax.security.auth.Subject

import aug.script.framework.ReloadData
import aug.util.LoremIpsum
import org.scalatest.FlatSpec

import scala.collection.mutable.ListBuffer


class ReloaderTest extends FlatSpec {

  "a listbuffer" should "reload" in {
    object Subject {
      @Reload private[ReloaderTest] val listBuffer = ListBuffer[String]()
    }

    Subject.listBuffer += "one"
    val rl = new ReloadData

    Reloader.save(rl, List(Subject)).foreach(e=> throw e)
    Subject.listBuffer.clear
    assert(Subject.listBuffer.isEmpty)
    Reloader.load(rl, List(Subject)).foreach(e=> throw e)
    assert(Subject.listBuffer.size == 1)
    assert(Subject.listBuffer.head == "one")
  }

  "a large list" should "reload" in {
    object Subject {
      @Reload private[ReloaderTest] val listBuffer = ListBuffer[String]()
    }

    for (i <- 1 to 1000) {
      Subject.listBuffer += LoremIpsum.randomWord
    }

    val lb = Subject.listBuffer.clone()
    val rl = new ReloadData

    Reloader.save(rl, List(Subject)).foreach(e=> throw e)
    Subject.listBuffer.clear
    assert(Subject.listBuffer.isEmpty)
    Reloader.load(rl, List(Subject)).foreach(e=> throw e)
    assert(Subject.listBuffer.size == 1000)
    assert(Subject.listBuffer == lb)
  }

  "java subject" should "reload" in {
    JavaReloadSubject.test()
  }

  "a java ArrayList" should "reload" in {
    object Subject {
      @Reload private[ReloaderTest] val arrayList = new util.ArrayList[String]()
    }

    Subject.arrayList.add("one")
    val rl = new ReloadData

    Reloader.save(rl, List(Subject)).foreach(e=> throw e)
    Subject.arrayList.clear()
    assert(Subject.arrayList.isEmpty)
    Reloader.load(rl, List(Subject)).foreach(e=> throw e)
    assert(Subject.arrayList.size == 1)
    assert(Subject.arrayList.get(0) == "one")
  }

  "instances" should "reload" in {

    val instances = for(i <- 1 to 1000) yield new ValueClass(i.toLong)

    println(instances(0).num)

    val rl = new ReloadData

    classOf[ValueClass].getConstructors.foreach(println)

    Reloader.saveInstances(rl, instances.toList).foreach(e=> throw e)

    val (loaded, exceptions) = Reloader.loadInstances(rl, classOf[ValueClass])
    exceptions.foreach(e => throw e)

    assert(loaded.size == 1000)
    loaded.foreach(s => s.getKey == s.num)
  }

}

class ValueClass() extends Reloadable {
  var key: Long = _

  def this(key: Long) {
    this()
    this.key = key
    this.num = key
  }

  @Reload var num: Long = 0l


  override def getKey: Long = key
  override def setKey(key: Long): Unit = this.key = key
}
