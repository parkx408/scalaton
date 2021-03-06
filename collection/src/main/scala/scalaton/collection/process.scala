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

package scalaton.collection

import scalaz.{ Ordering => _, _ }, Scalaz._
import scalaz.stream._, Process._
import scalaz.concurrent._
import argonaut._, Argonaut._
import scalaton.util._
import scalaton.util.paths._

object process {
  def grouped[I](n: Int): Process.Process1[I,Vector[I]] = {
    def go(m: Int, acc: Vector[I]): Process.Process1[I,Vector[I]] =
      if (m <= 0) emit(acc) ++ go(n, Vector())
      else await1[I].flatMap(i => go(m - 1, acc :+ i))
    go(n, Vector.empty)
  }

  def group[I](p: (I, I) => Boolean): Process.Process1[I,Vector[I]] = {
    def go(acc: Vector[I], prev: I): Process.Process1[I,Vector[I]] =
      await1[I].flatMap{ i =>
        if (p(prev, i)) go(acc :+ i, i)
        else emit(acc) ++ go(Vector(i), i)
      }

    await1[I].flatMap(i => go(Vector(i), i))
  }

  def iterator[A](iterator: =>Iterator[A], close: =>Unit = ()): Process[Task,A] =
    io.resource(Task.delay(\/.fromTryCatch(iterator)))(
      _ => Task.delay(close))(
      disj => Task.delay{ disj.fold(e => throw e , iter => if (iter.hasNext) iter.next else throw Process.End )})


  object Implicits {
    implicit class ProcessOps[F[_],A](p: Process[F, A]) {
      def grouped(n: Int) = p |> process.grouped(n)
      def group(pr: (A,A) => Boolean) = p |> process.group(pr)
    }
    implicit class ProcessTaskOps[A](p: Process[Task, A]) {
      def sort(groupSize: Int, tmp: Path)(implicit e: EncodeJson[A], d: DecodeJson[A], o: Ordering[A]) =
        ExternalSort.sortBy(p, groupSize, tmp)(identity)

      def sort(groupSize: Int)(implicit e: EncodeJson[A], d: DecodeJson[A], o: Ordering[A], osSpecific: OSSpecific) =
        ExternalSort.sort(p, groupSize, Filesystem.mkTempDir())

      def sortBy[K : Ordering](groupSize: Int)(key: A => K)(implicit e: EncodeJson[A], d: DecodeJson[A], o: Ordering[A], osSpecific: OSSpecific) =
        ExternalSort.sortBy(p, groupSize, Filesystem.mkTempDir())(key)
    }
  }

}
