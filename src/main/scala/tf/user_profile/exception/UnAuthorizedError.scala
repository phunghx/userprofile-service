package tf.user_profile.exception

import com.twitter.finagle.http.Status

/**
 * @author anhlt
 */
case class UnAuthorizedError(message:  Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.Unauthorized,message,cause) {
  override def getStatus: Status = Status.Unauthorized
}
