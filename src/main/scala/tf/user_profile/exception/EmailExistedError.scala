package tf.user_profile.exception

import com.twitter.finagle.http.Status

/**
 * @author anhlt
 */
case class EmailExistedError(message: Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.EmailExisted,message,cause ) {
  override def getStatus: Status = Status.BadRequest
}
