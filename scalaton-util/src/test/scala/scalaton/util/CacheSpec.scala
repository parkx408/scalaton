package scalaton.util.caching

import org.specs2.mutable._
import scalaz._
import Scalaz._


class CacheSpec extends Specification {

  "An LRU cache" should {
    "hold recently used items" in {
      val lru = new LruCache[Int](100)

      lru update("a", 1)

      lru update("b", 100)

      (lru get "a") mustEqual Some(1)

      (lru get "b") mustEqual Some(100)

      lru("a") mustEqual 1
    }

    "evict least recently used items when full" in {
      val lru = new LruCache[Int](3)

      lru update("a", 1)

      lru update("b", 100)

      lru update("c", 4)

      lru update("d", 30)

      (lru get "a") mustEqual None

      (lru get "b") mustEqual Some(100)

      (lru get "c") mustEqual Some(4)

      (lru get "d") mustEqual Some(30)

      lru update("c", 25)

      (lru get "c") mustEqual Some(25)
    }

  }

  "An expiring LRU cache" should {
    "hold recently used items before expiration" in {
      val lru = new ExpiringLruCache[Int](100, 16, 1000, 1000, 0, 1000, 0.9)

      lru update("a", 1)

      lru update("b", 2)

      lru update("c", 3)

      (lru get "a") mustEqual Some(1)

      (lru get "b") mustEqual Some(2)

      (lru get "c") mustEqual Some(3)

      Thread.sleep(1100)

      (lru get "a") mustEqual None

      lru.keySet mustEqual Set("b", "c")

      lru update("d", 4)

      lru.keySet mustEqual Set("d")
    }

    "clean up expired items on update" in {
      val lru = new ExpiringLruCache[Int](100, 16, 1000, 1000, 0, 2000, 0.9)

      lru update("a", 1)

      lru update("b", 2)

      lru update("c", 3)

      (lru get "a") mustEqual Some(1)

      (lru get "b") mustEqual Some(2)

      (lru get "c") mustEqual Some(3)

      Thread.sleep(1100)

      (lru get "a") mustEqual None

      lru.keySet mustEqual Set("b", "c")

      lru update("d", 4)

      lru.keySet mustEqual Set("b", "c", "d")

      Thread.sleep(1100)

      lru update("d", 5)

      lru.keySet mustEqual Set("d")
    }

  }
}
