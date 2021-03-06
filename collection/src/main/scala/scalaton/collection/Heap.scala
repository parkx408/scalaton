/*
 Copyright 2013 Elliot Chow

 Licensed under the Apache License, Version 2.0 (the "License")
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package scalaton.collection.immutable

import scala.collection._
import scala.collection.generic._
import scala.collection.mutable


/** Pairing heap implementation http://www.lb.cs.cmu.edu/afs/cs.cmu.edu/user/sleator/www/papers/pairing-heaps.pdf */
sealed abstract class Heap[A] extends Iterable[A] with GenericOrderedTraversableTemplate[A, Heap] with IterableLike[A, Heap[A]] {

  override val orderedCompanion = Heap

  override def newBuilder: mutable.Builder[A, Heap[A]] = Heap.newBuilder[A]

  /** insert element into heap (O(1)) */
  def +(elem: A): Heap[A]

  /** read the min value (O(1)) */
  def min: A

  /** read the min value and return the heap after removing the min (O(log n)) */
  def removeMin: (A, Heap[A])

  /** merge 2 heaps together (O(1)) */
  def merge(that: Heap[A]): Heap[A]

  override def iterator = sortedStream.iterator

  def sorted: Seq[A] = sortedStream

  private[immutable] def sortedStream: Stream[A] = this match {
    case HeapEmpty() => Stream.empty
    case t: HeapNode[A] =>
      val (next, rest) = this.removeMin
      next #:: rest.sortedStream
  }

}

case class HeapNode[A](val min: A, override val size: Int, children: Vector[Heap[A]])(implicit protected[this] val ord: Ordering[A]) extends Heap[A] {
  require(size > 0, "heap node must have strictly positive size")

  def +(elem: A) = this.merge(HeapNode(elem, 1, Vector.empty))

  def removeMin: (A, Heap[A]) = {
    val h = if (size == 1) {
      HeapEmpty()
    } else {
      val paired = children.grouped(2).map{ hs => if (hs.size == 2) hs(0).merge(hs(1)) else hs(0) }.toSeq
      paired.reverse.foldLeft(HeapEmpty[A](): Heap[A]){ case (h, hh) => h.merge(hh)  }
    }

    (min, h)
  }

  def merge(that: Heap[A]): Heap[A] = that match {
    case HeapEmpty() => this

    case that: HeapNode[A] => if (ord.lt(this.min,that.min)) this.linkToLeft(that) else that.linkToLeft(this)
  }

  override def isEmpty = false

  private[immutable] def linkToLeft(that: Heap[A]) = this.copy(size = this.size + that.size, children = that +: this.children)

}

case class HeapEmpty[A](implicit ord: Ordering[A]) extends Heap[A] {
  def +(elem: A): Heap[A] = HeapNode(elem, 1, Vector.empty)

  def min: A = throw new NoSuchElementException("empty heap has no minimum")

  def removeMin: (A, Heap[A]) = throw new NoSuchElementException("empty heap has no minimum")

  def merge(that: Heap[A]): Heap[A] = that

  override def iterator = Iterator.empty

  override def isEmpty = true

  override val size = 0
}

object Heap extends GenericOrderedCompanion[Heap] {

  override def empty[A : Ordering]: Heap[A] = HeapEmpty[A]()

  override def apply[A : Ordering](xs: A*) = fromIterable(xs)

  def fromIterable[A : Ordering](xs: Iterable[A]) = xs.foldLeft(empty[A])((h, x) => h + x)

  def heapsort[A : Ordering](xs: Iterable[A]) = fromIterable(xs: Iterable[A]).sorted

  def newBuilder[A : Ordering]: mutable.Builder[A, Heap[A]] =
    new mutable.ArrayBuffer[A] mapResult { xs => Heap(xs: _*) }

  implicit def canBuildFrom[A : Ordering]
      : CanBuildFrom[Heap[_], A, Heap[A]] =
    new CanBuildFrom[Heap[_], A, Heap[A]] {
      def apply(from: Heap[_]) = newBuilder[A]
      def apply() = newBuilder[A]
    }

}
