package datainsider.user_profile.domain.request

import datainsider.user_profile.util.Utils

/**
 * @author anhlt
 */
case class VerifyCodePhoneRequest(phoneNumber: String, verifyCode: String) {
  val normalizedPhoneNumber = Utils.normalizePhoneNumber(phoneNumber)
}
