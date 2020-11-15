package tf.user_profile.exception

case class ExpiredError(message: Option[String] = None, cause: Throwable = null) extends XedException(XedException.Expired, message,cause) {
  override def getStatus = com.twitter.finagle.http.Status.Gone
}
