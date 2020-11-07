package datainsider.user_profile.domain.request

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author anhlt
 */
case class SendCodeToPhoneBodyRequest(
  @JsonProperty("phone_number") phoneNumber: String,
  @JsonProperty("token_captcha") tokenCaptcha: Option[String] = None
)