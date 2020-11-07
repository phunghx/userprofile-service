package datainsider.user_profile.domain.response

import datainsider.user_profile.domain.profile.{SessionInfo, UserInfo, UserProfile}

/**
 * @author anhlt
 */
case class UserResponse(
  code: Int,
  userInfo: UserInfo = null,
  userProfile: Option[UserProfile] = None,
  msg: String = null,
  session: Option[SessionInfo] = None,
  defaultOAuthCredential: Option[Boolean] = None
)
