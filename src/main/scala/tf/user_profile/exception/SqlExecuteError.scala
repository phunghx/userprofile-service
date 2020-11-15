package tf.user_profile.exception

case class SqlExecuteError(message: Option[String] = None, cause: Throwable = null) extends XedException(XedException.SqlExecuteFailure, message,cause) {
  override def getStatus = com.twitter.finagle.http.Status.BadRequest
}