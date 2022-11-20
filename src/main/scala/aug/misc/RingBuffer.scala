package aug.misc

import scala.reflect.ClassTag

class RingBuffer[A](val capacity: Int)(implicit m: ClassTag[A]) extends scala.collection.mutable.IndexedSeq[A] {
  private val data: Array[A] = new Array[A](capacity)
  private var index = 0
  var length = 0

  private def off(idx: Int) : Int = (index -idx+capacity) % capacity

  def push(elem: A) : Unit = {
    if(length < capacity) length += 1
    index = (1+index)%length
    data(index) = elem
  }

  def apply(idx: Int) : A = {
    if(idx < 0 || idx >= capacity) throw new IndexOutOfBoundsException
    data(off(idx))
  }

  override def update(idx: Int, elem: A): Unit = {
    if(idx < 0 || idx >= length) throw new IndexOutOfBoundsException
    data(off(idx)) = elem
  }
}
