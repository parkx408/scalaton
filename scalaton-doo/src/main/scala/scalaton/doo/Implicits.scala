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

package scalaton.doo

import collection.mutable

import com.github.nscala_time.time.Imports._

import java.io._
import org.apache.hadoop.conf.{Configuration => HConf}

import scalaton.util._
import scalaton.util.hashing._
import scalaton.util.hashing32._

import com.nicta.scoobi.Scoobi._
import com.nicta.scoobi.core.{Reduction, ScoobiConfiguration}

import com.typesafe.scalalogging.slf4j._

import scalaz.{DList => _, _}
import Scalaz._

trait ImplicitConversions{

  // DLists
  implicit class DListRich[A : Manifest : WireFormat](val dl: DList[A]) extends Logging{

    def partitionAtRandom(n: Int, seed: Int = 0) = sampling.partitionAtRandom(dl, n, seed)

    def limit(n: Int = 0) = sampling.limit(dl, n)

    def parallelFold[B : Manifest : WireFormat](init: B)(f: (B, A) => B) =
      helpers.parallelFold(dl, init)(f)

    def parallelFoldMonoid[B : Manifest : WireFormat : Monoid](f: (B, A) => B) =
      helpers.parallelFoldMonoid(dl)(f)

    def sample(rate: Double, seed: Int = 0)(implicit hashable: Hashable[A,Bits32]) = sampling.sample(dl, rate, seed)

    def sampleBy[B : Manifest : WireFormat](f: A => B)(rate: Double, seed: Int = 0)(implicit hashable: Hashable[B,Bits32]) = sampling.sampleBy(dl.map(a => (f(a), a)), rate, seed)

    def cache(path: String, overwrite: Boolean = false, conf: HConf = new HConf)(implicit sc: ScoobiConfiguration): DList[A] = {
      if(overwrite) {
        logger info s"deleting cached data from $path"

        hdfs.delete(path, true, conf)
      }

      if(hdfs.exists(path, conf)){
        logger info s"loading cached data from $path"

        valueFromSequenceFile[A](path)
      }else{
        logger info s"caching data to $path"

        persist(dl.map(x => (1, x)).toSequenceFile(path, overwrite))

        dl
      }
    }

  }

  implicit class DList2RichGroupingA[A : Manifest : WireFormat : Grouping, B : Manifest : WireFormat](val dl: DList[(A,B)]){
    def bloomJoin[BR : Manifest : WireFormat](right: DList[(A,BR)], expectedNumKeys: Int)(implicit hashable: Hashable[A,Bits32]) = joins.bloomJoin(dl, right, expectedNumKeys)

    def skewedJoin[BR : Manifest : WireFormat](right: DList[(A,BR)], sampleRate: Double, maxPerReducer: Int)(implicit hashable: Hashable[A,Bits32]) = joins.skewedJoin(dl, right, sampleRate, maxPerReducer)

    def groupByKeyThenCombine(implicit semigroupB: Semigroup[B]) = helpers.groupByKeyThenCombine(dl)
  }

  // Wireformats

  implicit val jodaLocalDateWF = AnythingFmt[LocalDate]

  implicit def validationFmt[E : WireFormat, A : WireFormat] = new ValidationWireFormat[E, A]
  class ValidationWireFormat[E, A](implicit wt1: WireFormat[E], wt2: WireFormat[A]) extends WireFormat[Validation[E, A]] {
    def toWire(x: Validation[E, A], out: DataOutput) = x match {
      case Failure(x) => { out.writeBoolean(true); wt1.toWire(x, out) }
      case Success(x) => { out.writeBoolean(false); wt2.toWire(x, out) }
    }

    def fromWire(in: DataInput): Validation[E, A] = {
      val isFailure = in.readBoolean()
      if (isFailure) {
        val x: E = wt1.fromWire(in)
        Failure(x)
      } else {
        val x: A = wt2.fromWire(in)
        Success(x)
      }
    }

    override def toString = "Validation["+wt1+","+wt2+"]"
  }

  implicit def nonEmptyListWF[A : WireFormat] = new NonEmptyListWireFormat[A]
  class NonEmptyListWireFormat[A](implicit wt: WireFormat[A]) extends WireFormat[NonEmptyList[A]]{
    def toWire(x: NonEmptyList[A], out: DataOutput) = {
      wt.toWire(x head, out)
      implicitly[WireFormat[List[A]]].toWire(x tail, out)
    }

    def fromWire(in: DataInput): NonEmptyList[A] = {
      val h = wt.fromWire(in)
      val t = implicitly[WireFormat[List[A]]].fromWire(in)
      NonEmptyList(h, t : _*)
    }

    override def toString = "NonEmptyList["+wt+"]"
  }

  // Reductions

  implicit def funToReduction[A](f: (A, A) => A) = Reduction(f)

}

object implicits extends ImplicitConversions
