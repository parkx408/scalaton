package scalaton.aggregate.hashed

import scala.language.higherKinds
import scala.language.postfixOps

import scalaz.{BloomFilter => _, _}
import Scalaz._

import scalaton.util._
import scalaton.util.hashable._


/**
 * Type classes used for defining different hashed collections
 */

/** Characterizes a collection that will hash items (consistently) **/
trait Hashes[A,B,C]{

  /** Number of hashes used in this collection **/
  val numHashes: Int

  /** Seed of hashing function **/
  val seed: Long

  /** Compute hash of an item **/
  def hashItem(item: A)(implicit h: Hashable[A, B],
                        hconv: HashCodeConverter[B, C]): Iterable[C @@ HashCode] = {
    (multiHash(item, seed)(h) |> hconv.convertSeq take numHashes)
  }

}

/** Collections in which you can insert hashed items **/
trait HashedCollection[A,B,C,F] extends Hashes[A,B,C]

trait Insertable[A,B,C,T,F] extends HashedCollection[A,B,C,F]{
  def insert(collection: F, item: A)(implicit mon: Monoid[T],
                                     h: Hashable[A, B],
                                     hconv: HashCodeConverter[B, C]): F
}

/** Can instantiate with a single item **/
trait MakesSingleton[A,B,C,T,F] extends HashedCollection[A,B,C,F] with Insertable[A,B,C,T,F]with Monoid[F] {
  def singleton(item: A)(implicit i: Insertable[A,B,C,T,F],
                         mon: Monoid[T],
                         h: Hashable[A, B],
                         hconv: HashCodeConverter[B, C]): F =
    insert(zero, item)
}

/** Can check for existence of an item **/
trait MapLike[A,B,C,T,R,F] extends HashedCollection[A,B,C,F]{
  def get(collection: F, item: A)(implicit v: Value[T,R],
                                  h: Hashable[A, B],
                                  hconv: HashCodeConverter[B, C]): R
}

/** Can check for existence of an item **/
trait SetLike[A,B,C,F] extends HashedCollection[A,B,C,F]{
  def contains(collection: F, item: A)(implicit h: Hashable[A, B],
                                       hconv: HashCodeConverter[B, C]): Boolean
}

/** Can check the size/cardinality **/
trait Sized[F]{
  def cardinality(collection: F): Long
}



/**
 * Functions for operating on hashed collections
 **/

trait HashedCollectionFunctions{
  def insert[A,B,C,T,F](collection: F, item: A)(implicit i: Insertable[A,B,C,T,F],
                                                mon: Monoid[T],
                                                h: Hashable[A, B],
                                                hconv: HashCodeConverter[B, C]) =
    i.insert(collection, item)
}



trait MakesSingletonFunctions{
  def singleton[A,B,C,T,F](item: A)(implicit ms: MakesSingleton[A,B,C,T,F],
                                    mon: Monoid[T],
                                    h: Hashable[A, B],
                                    hconv: HashCodeConverter[B, C]): F =
    ms.singleton(item)
}


trait SetLikeFunctions{
  def contains[A,B,C,F](collection: F, item: A)(implicit c: SetLike[A,B,C,F],
                                                h: Hashable[A, B],
                                                hconv: HashCodeConverter[B, C]) =
    c.contains(collection, item)
}

trait MapLikeFunctions{
  def get[A,B,C,T,R,F](collection: F, item: A)(implicit m: MapLike[A,B,C,T,R,F],
                                               v: Value[T,R],
                                               h: Hashable[A, B],
                                               hconv: HashCodeConverter[B, C]) =
    m.get(collection, item)
}

trait SizedFunctions{
  def cardinality[F](collection: F)(implicit s: Sized[F]): Long =
    s.cardinality(collection)
}


