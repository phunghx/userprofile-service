package tf.user_profile.service.synchronizer

import java.util.concurrent.atomic.AtomicBoolean

import com.twitter.inject.Logging
import tf.user_profile.util.Utils

/**
 * @author anhlt
 */
abstract class SchedulerService(intervalTimeInMs: Long) extends Thread with Logging {
  val isRunning = new AtomicBoolean(false)
  val sleepBefore = 3 * 60 * 1000l

  def exec(): Unit

  override def run(): Unit = {
    info(s"Sleep $sleepBefore ms =>[${getClass.getSimpleName}] running")
    Thread.sleep(sleepBefore)
    while (isRunning.get()) {
      exec()
      val startTimestamp = System.currentTimeMillis()
      val time = Utils.roundTimeByInterval(startTimestamp, intervalTimeInMs)
      val sleepTime = (time + intervalTimeInMs) - startTimestamp
      info(s"[${getClass.getSimpleName}] will be sleep $sleepTime mills")
      if (sleepTime > 0) {
        Thread.sleep(sleepTime)
      }
    }
  }

  override def start(): Unit = {
    isRunning.set(true)
    super.start()
  }

  def stopSafe(): Unit = isRunning.set(false)
}
