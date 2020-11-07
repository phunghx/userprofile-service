package datainsider.user_profile.domain.request

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @author anhlt
 */
case class VerifyCodeEmailRequest(
  @JsonProperty("email") email: String,
  @JsonProperty("verify_code") verifyCode: String
)
