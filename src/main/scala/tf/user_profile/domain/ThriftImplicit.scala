package tf.user_profile.domain

import com.twitter.scrooge.{Response, ThriftStruct}
import tf.user_profile.domain.profile.UserInfo
import tf.user_profile.domain.thrift.TSessionInfo
import tf.user_profile.domain.profile.{SessionInfo, UserInfo, UserProfile}
import tf.user_profile.domain.thrift.{TSessionInfo, TUserInfo, TUserProfile}

/**
 * @author anhlt
 */
object ThriftImplicit {
  implicit class ScroogeResponseLike[T<:ThriftStruct](struct: T) {
    def toScroogeResponse():Response[T] = Response(struct)
  }

  implicit def Session2T(sessionInfo: SessionInfo): TSessionInfo = {
    TSessionInfo(sessionInfo.key, sessionInfo.value, sessionInfo.timeoutInMS, sessionInfo.domain, Some(sessionInfo.path))
  }

  implicit def OptionSession2T(op: Option[SessionInfo]): Option[TSessionInfo] = {
    op match {
      case Some(x) => Some(x)
      case _ => None
    }
  }

  implicit def T2UserInfo(caasTUserInfo: user_caas.domain.thrift.TUserInfo): UserInfo = {
    UserInfo(caasTUserInfo.username, caasTUserInfo.roles.map(_.id), caasTUserInfo.isActive, caasTUserInfo.createTime)
  }

  implicit def UserInfo2T(userInfo: UserInfo): TUserInfo = {
    TUserInfo(userInfo.username, userInfo.isActive, userInfo.createTime, userInfo.roles)
  }

  implicit def UserProfile2T(userProfile: UserProfile): TUserProfile = {
    TUserProfile(
      username = userProfile.username,
      fullName = userProfile.fullName,
      email = userProfile.email,
      avatar = userProfile.avatar,
      mobilePhone = userProfile.phone,
      lastName = userProfile.lastName,
      firstName = userProfile.firstName,
      nationality = userProfile.nationality,
      nativeLanguages = userProfile.nativeLanguages,
      additionalInfo = userProfile.additionalInfo
    )
  }

  implicit def SeqUserProfile2T(userProfiles: Seq[UserProfile]): Seq[TUserProfile] = {
    userProfiles.map(f => f: TUserProfile)
  }

  implicit def UserProfile2TOption(userProfile: Option[UserProfile]): Option[TUserProfile] = {
    userProfile match {
      case Some(x) => Some(x)
      case None => None
    }
  }

  implicit def CaasUserInfoToProfileUserInfo(caasUserInfo: user_caas.domain.thrift.TUserInfo): TUserInfo = {
    TUserInfo(
      caasUserInfo.username,
      caasUserInfo.isActive,
      caasUserInfo.createTime,
      caasUserInfo.roles.map(x => x.id)
    )
  }
}
