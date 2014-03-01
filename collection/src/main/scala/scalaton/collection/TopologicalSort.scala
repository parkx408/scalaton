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

import scalaz._, Scalaz._

object TopologicalSort {
  def empty[A]: TopologicalSort[A] = new TopologicalSort[A](Set.empty, Map.empty)

  def apply[A](dependencies: Iterable[(A,A)]): TopologicalSort[A] =
    empty[A] ++ dependencies

  def apply[A](dependencies: (A, A)*): TopologicalSort[A] =
    apply(dependencies)

}

/** simple topological sort implementation - does NOT check for cycles (should throw IllegalStateException if a cycle is encountered) **/
class TopologicalSort[A] private (val independents: Set[A], val dependencyMap: Map[A,Set[A]]) extends Iterable[A] { self =>
  if(independents.isEmpty && dependencyMap.nonEmpty)
    throw new IllegalStateException("unreachable elements in topological sort graph")

  def +(dep: (A, A)): TopologicalSort[A] = dep match {
    case (before, after) =>
      new TopologicalSort(
        independents - after ++ (if (hasDependencies(before)) Set.empty else Set(before)),
        dependencyMap |+| Map(after -> Set(before))
      )
  }

  def ++(deps: Iterable[(A, A)]) =
    deps.foldLeft(this){ case (t, (before, after)) => t + (before -> after) }

  def addDependents(dep: (A, Set[A])): TopologicalSort[A] = dep match {
    case (before, afters) => this ++ afters.map(after => (before, after))
  }

  def addDependencies(dep: (Set[A], A)): TopologicalSort[A] = dep match {
    case (befores, after) => this ++ befores.map(before => (before, after))
  }

  def pop: (Set[A], TopologicalSort[A]) = {
    val (newIndependents, newDependencyMap) = dependencyMap.foldLeft((Set.empty[A], Map.empty[A,Set[A]])){ case ((is, map), (after, befores0)) =>
      val befores = befores0 -- independents
      if (befores.isEmpty) (is + after, map) else (is, map + (after -> befores))
    }

    (independents, new TopologicalSort(newIndependents, newDependencyMap))
  }

  def iterator: Iterator[A] = new Iterator[A] {
    private var t = self
    private var is = Vector.empty[A]

    def hasNext = is.nonEmpty || t.nonEmpty

    def next = {
      if (is.nonEmpty) {
        val i = is.head
        is = is.drop(1)
        i
      } else {
        val nxt = t.pop
        is = nxt._1.toVector
        t = nxt._2
        next
      }
    }
  }

  override def isEmpty = independents.isEmpty && dependencyMap.isEmpty
  override def nonEmpty = !isEmpty

  def dependenciesOf(a: A) = dependencyMap.getOrElse(a, Set.empty)
  def hasDependencies(a: A) = dependenciesOf(a).nonEmpty

  override def toString = s"TopologicalSort($independents, $dependencyMap)"

}
