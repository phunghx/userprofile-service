package datainsider.user_profile.exception

import com.twitter.finagle.http.Status

/**
 * @author anhlt
 */
case class RegistrationRequiredError(message:  Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.RegistrationRequired,message, cause){
  override def getStatus: Status = Status.BadRequest
}
