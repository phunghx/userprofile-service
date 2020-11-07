package datainsider.user_profile.domain.profile

/**
 * @author anhlt
 */

case class UserFullInfoPageable(total: Long,
                                data: Option[Seq[UserFullInfo]])

case class UserFullInfo(userInfo: UserInfo,
                        userProfile: Option[UserProfile] = None)

case class UserAuthInfoResponse(session: SessionInfo,
                                userInfo: UserInfo,
                                userProfile: Option[UserProfile] = None,
                                defaultOAuthCredential: Option[Boolean] = None)
