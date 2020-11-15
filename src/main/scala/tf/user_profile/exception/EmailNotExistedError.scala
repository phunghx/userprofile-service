package tf.user_profile.exception

import com.twitter.finagle.http.Status

/**
 * @author anhlt
 */
case class EmailNotExistedError(message:  Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.EmailNotExisted,message, cause) {
  override def getStatus: Status = Status.BadRequest
}
