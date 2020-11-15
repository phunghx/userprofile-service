package tf.user_profile.domain.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.twitter.finagle.http.Request
import javax.inject.Inject
import tf.user_profile.util.Utils

/**
 * @author anhlt
 */
case class RegisterUserBodyRequest(@JsonProperty("full_name") fullName: String,
                                   email: String,
                                   password: String)

case class ForgetPasswordBodyRequest(
                                    email: String,
                                    @JsonProperty("new_password") newPassword: String,
                                    @JsonProperty("email_token") emailToken: String
                                  )

case class RegisterOAuthUserBodyRequest(
  @JsonProperty("phone_token") phoneToken: Option[String],
  @JsonProperty("oauth_type") oauthType: String,
  id: String,
  token: String,
  @JsonProperty("phone_number") phoneNumber: Option[String],
  @JsonProperty("verify_code") verifyCode: Option[String],
  password: String) {

  val normalizedPhoneNumber: Option[String] = phoneNumber match {
    case Some(x) => Some(Utils.normalizePhoneNumber(x))
    case _ => None
  }
}

case class UserOAuthBodyRequest(
  @JsonProperty("oauth_type") oauthType: String,
  id: String,
  token: String
)

case class UpdatePhoneBodyRequest(
  @JsonProperty("phone_number") phoneNumber: String,
  @JsonProperty("verify_code") verifyCode: String
) {
  val normalizedPhoneNumber = Utils.normalizePhoneNumber(phoneNumber)
}

case class UpdateEmailBodyRequest(email: String)

case class UpdateNameRequest(name: String, @Inject request: Request)