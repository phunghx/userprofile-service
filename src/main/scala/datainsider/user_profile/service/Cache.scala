package datainsider.user_profile.service

import java.util.concurrent.TimeUnit

import com.google.common.cache._
import com.google.common.util.concurrent.{JdkFutureAdapters, ListenableFuture}

import scala.collection.JavaConversions._

/**
 * @author anhlt
 */

trait Cache[K, V] {
  def removeNotification(key: K, oldVal: V): Unit

  def newInstanceValue(): V

  def loadData(key: K): V

  def reloadData(key: K, oldValue: V): com.twitter.util.Future[V]

  def get(key: K): V

  def putAll(m: Map[K, V]): Unit

  def put(key: K, value: V): Unit
}

abstract class CommonCache[K <: Object, V <: Object] extends Cache[K, V] {
  override def removeNotification(key: K, oldVal: V): Unit = {}

  override def loadData(key: K) = newInstanceValue()

  protected val cacheLoader: CacheLoader[K, V] = new CacheLoader[K, V] {
    override def load(key: K): V = loadData(key)

    override def reload(key: K, oldValue: V): ListenableFuture[V] = JdkFutureAdapters.listenInPoolThread(
      reloadData(key, oldValue).toJavaFuture.asInstanceOf[java.util.concurrent.Future[V]]
    )
  }
}

abstract class TimeRefreshCache[K <: Object, V <: Object](intervalTimeInMs: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .refreshAfterWrite(intervalTimeInMs, TimeUnit.MILLISECONDS)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}

abstract class LRUCache[K <: Object, V <: Object](maxSize: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .maximumSize(maxSize)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}

abstract class LRURefreshCache[K <: Object, V <: Object](intervalTimeInMs: Long, maxSize: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .maximumSize(maxSize)
    .refreshAfterWrite(intervalTimeInMs, TimeUnit.MILLISECONDS)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}

abstract class TimeEvictRefreshCache[K <: Object, V <: Object](expiredTimeInMs: Long, intervalRefreshTimeInMs: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .refreshAfterWrite(intervalRefreshTimeInMs, TimeUnit.MILLISECONDS)
    .expireAfterAccess(expiredTimeInMs, TimeUnit.MILLISECONDS)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}

abstract class LRURefreshTimeEvictCache[K <: Object, V <: Object](expiredTimeInMs: Long, intervalRefreshTimeInMs: Long, maxSize: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .maximumSize(maxSize)
    .refreshAfterWrite(intervalRefreshTimeInMs, TimeUnit.MILLISECONDS)
    .expireAfterAccess(expiredTimeInMs, TimeUnit.MILLISECONDS)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}

abstract class LRUTimeEvictCache[K <: Object, V <: Object](expiredTimeInMs: Long, maxSize: Long) extends CommonCache[K, V] {
  val cache = CacheBuilder.newBuilder()
    .removalListener(
      new RemovalListener[K, V]() {
        override def onRemoval(notification: RemovalNotification[K, V]): Unit = removeNotification(notification.getKey, notification.getValue)
      }
    )
    .maximumSize(maxSize)
    .expireAfterAccess(expiredTimeInMs, TimeUnit.MILLISECONDS)
    .build[K, V](cacheLoader)

  override def get(key: K): V = {
    cache.getIfPresent(key) match {
      case null => refresh(key)
      case x => x
    }
  }

  def refresh(key: K) = {
    val value = loadData(key)
    cache.put(key, value)
    cache.refresh(key)
    value
  }

  override def putAll(m: Map[K, V]): Unit = cache.putAll(m)

  override def put(key: K, value: V): Unit = cache.put(key, value)
}