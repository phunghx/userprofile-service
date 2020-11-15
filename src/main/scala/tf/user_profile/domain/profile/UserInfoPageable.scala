package tf.user_profile.domain.profile

/**
 * @author anhlt
 */
case class UserInfoPageable(total: Long, users: Option[Seq[UserInfo]])
