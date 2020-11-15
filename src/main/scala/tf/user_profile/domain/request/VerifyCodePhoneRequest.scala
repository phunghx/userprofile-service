package tf.user_profile.domain.request

import tf.user_profile.util.Utils

/**
 * @author anhlt
 */
case class VerifyCodePhoneRequest(phoneNumber: String, verifyCode: String) {
  val normalizedPhoneNumber = Utils.normalizePhoneNumber(phoneNumber)
}
