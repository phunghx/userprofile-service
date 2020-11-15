package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.phone.PhoneQuotaFilterRequest
import datainsider.user_profile.util.{JsonParser, Utils}
import tf.user_profile.controller.http.filter.phone.{PhoneFilterRequest, PhoneQuotaFilterRequest}
import tf.user_profile.domain.request.SendCodeToPhoneBodyRequest
import tf.user_profile.util.{JsonParser, Utils}

/**
 * @author anhlt
 */
case class SendCodeToPhoneRequest(phoneNumber: String, tokenCaptcha: Option[String]) extends PhoneQuotaFilterRequest with PhoneFilterRequest {
  val normalizedPhoneNumber = Utils.normalizePhoneNumber(phoneNumber)

  override def getPhoneForQuota(): String = normalizedPhoneNumber

  override def getTokenCaptcha(): Option[String] = tokenCaptcha

  override def getPhone(): Option[String] =Some(normalizedPhoneNumber)
}

class SendCodeToPhoneParser extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val sendPhoneRequest = JsonParser.fromJson[SendCodeToPhoneBodyRequest](request.contentString)
    val registerByPhoneRequest = SendCodeToPhoneRequest(sendPhoneRequest.phoneNumber, sendPhoneRequest.tokenCaptcha)
    DataRequestContext.setDataRequest(request, registerByPhoneRequest)
    service(request)
  }
}