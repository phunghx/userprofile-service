package tf.user_profile.domain.request

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author anhlt
 */
case class SendCodeToEmailBodyRequest(
  @JsonProperty("email") email: String,
  @JsonProperty("token_captcha") tokenCaptcha: Option[String] = None
)