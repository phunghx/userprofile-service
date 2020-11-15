package tf.user_profile.exception

case class InternalError(message: Option[String] = None, cause: Throwable = null) extends XedException(XedException.InternalError, message,cause) {
  override def getStatus = com.twitter.finagle.http.Status.InternalServerError
}