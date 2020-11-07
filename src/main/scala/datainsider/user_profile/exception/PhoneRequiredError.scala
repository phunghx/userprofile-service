package datainsider.user_profile.exception

import com.twitter.finagle.http.Status

/**
 * @author anhlt
 */
case class PhoneRequiredError(message:  Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.PhoneRequired,message, cause){
  override def getStatus: Status = Status.BadRequest
}
