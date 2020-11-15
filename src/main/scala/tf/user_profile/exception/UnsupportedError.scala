package tf.user_profile.exception

case class UnsupportedError(message: Option[String] = None, cause: Throwable = null)
  extends XedException(XedException.NotSupported, message,cause) {
  override def getStatus = com.twitter.finagle.http.Status.NotImplemented
}