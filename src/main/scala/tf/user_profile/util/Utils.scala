package tf.user_profile.util

import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils
import tf.user_profile.exception.NotFoundError

import scala.util.Random


/**
 * @author anhlt
 */
object Utils {
  val random = Random

  def randomInt(from: Int = Integer.MIN_VALUE, to: Int = Integer.MAX_VALUE): Int = {
    val randomVal = random.nextInt(to)
    if (randomVal < from) randomInt(from, to) else randomVal
  }

  def isValidEmail(email: String, emailRegex: String): Boolean = {
    val rex = emailRegex.r
    email match {
      case rex(email) => true
      case _ => false
    }
  }

  def isValidEmailV1(email: String, emailRegex: String): Boolean = {
    val rex = emailRegex.r
    rex.findFirstMatchIn(email) match {
      case Some(_) =>true
      case None => false
    }
  }

  def getNickname(email: Option[String], defaultValue: String = ""): String = {
    email match {
      case Some(x) => x.split("@")(0)
      case _ => defaultValue
    }
  }

  def normalizePhoneNumber(phoneNum: String): String = {
    var formatPhone = phoneNum
    if (phoneNum.startsWith("+")) {
      formatPhone = phoneNum.substring(1)
    } else if (phoneNum.startsWith("0")) {
      formatPhone = "84" + phoneNum.substring(1)
    }
    formatPhone
  }

  val timeUnits = Seq(
    (1l, Calendar.MILLISECOND),
    (TimeUnit.SECONDS.toMillis(1), Calendar.SECOND),
    (TimeUnit.MINUTES.toMillis(1), Calendar.MINUTE),
    (TimeUnit.HOURS.toMillis(1), Calendar.HOUR_OF_DAY),
    (TimeUnit.DAYS.toMillis(1), Calendar.DAY_OF_MONTH)
  )

  def roundTimeByInterval(time: Long, interval: Long): Long = {
    val cal = Calendar.getInstance()
    cal.setTimeInMillis(time)
    roundLevelByInterval(interval).foreach(i => cal.set(i, 0))
    incByItv(cal.getTimeInMillis, interval)(_ + interval < time)
  }


  def incByItv(initValue: Long, step: Long)(condition: Long => Boolean): Long =
    if (!condition(initValue))
      initValue
    else
      incByItv(initValue + step, step)(condition)


  private def roundLevelByInterval(interval: Long): Seq[Int] = {
    timeUnits.filter(_._1 <= interval).map(_._2)
  }

  def readBinaryFile(file: String): Array[Byte] = {
    FileUtils.readFileToByteArray(new File(file))
  }


  def throwIfNotExist[T](v : Option[T], msg: Option[String] = None) = v match  {
    case Some(x) => x
    case _ => throw NotFoundError(msg)
  }
}