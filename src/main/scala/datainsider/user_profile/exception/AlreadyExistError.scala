package datainsider.user_profile.exception

case class AlreadyExistError(message: Option[String] = None, cause: Throwable = null) extends XedException(XedException.AlreadyExisted, message,cause) {
  override def getStatus = com.twitter.finagle.http.Status.Conflict
}