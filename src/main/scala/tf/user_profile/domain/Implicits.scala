package tf.user_profile.domain

import com.twitter.util.{Future, FuturePool}
import tf.user_profile.domain.profile.UserProfile
import tf.user_profile.domain.profile.UserProfile
import tf.user_profile.repository.OAuthRepository
import user_caas.domain.thrift.Constants

/**
 * @author anhlt
 */
object Implicits {
  implicit val futurePool = FuturePool.unboundedPool


  def using[A <: AutoCloseable, B](a: A)(fn: A => B): B = {
    try {
      fn(a)
    } finally {
      if (a != null) {
        a.close()
      }
    }
  }


  implicit def async[A](f: => A): Future[A] = futurePool{f}

  implicit def val2Opt[A](value: A): Option[A] = Option(value)

  implicit def opt2Val[A](value: Option[A]): A = value.get

  implicit def UserOAuth2Profile(oAuthInfo: OAuthRepository, username: String): UserProfile = {
    UserProfile(
      username = username,
      fullName = oAuthInfo.getName,
      lastName = oAuthInfo.getFamilyName,
      firstName = oAuthInfo.getGivenName,
      email = oAuthInfo.getEmail,
      avatar = oAuthInfo.getAvatar,
      oauthType = Some(oAuthInfo.oauthType)
    )
  }

  def buildAvatar3Size(oauthType: Option[String], avatar: Option[String]): (Option[String], Option[String], Option[String]) = {
    avatar match {
      case Some(smallAvatar) => oauthType match {
        case Some(oauth) if oauth.equals(Constants.OAUTH_GOOGLE) => {
          val medium = smallAvatar.replaceAll("s96-c", "s400-c")
          val large = smallAvatar.replaceAll("s96-c", "s800-c")
          (Some(smallAvatar), Some(medium), Some(large))
        }
        case Some(oauth) if oauth.equals(Constants.OAUTH_FACEBOOK) => (Some(smallAvatar), Some(smallAvatar), Some(smallAvatar))
        case _ => (Some(smallAvatar), Some(smallAvatar), Some(smallAvatar))
      }
      case _ => (None, None, None)
    }
  }

  implicit def Option2String(v: Option[String]): String = {
    v.getOrElse("")
  }
}
