package datainsider.user_profile.domain.profile

/**
 * @author anhlt
 */
case class SessionInfo(key: String,
                       value: String,
                       domain: String,
                       timeoutInMS: Long,
                       path: String = "/") {
  val maxAge = System.currentTimeMillis() + timeoutInMS
}
