package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.util.JsonParser
import tf.user_profile.controller.http.filter.email.EmailFilterRequest
import tf.user_profile.util.JsonParser

/**
 * @author anhlt
 */
case class SendCodeToEmailRequest(email: String, tokenCaptcha: Option[String]) extends EmailFilterRequest {
  override def getEmail(): String = email
}

class SendCodeToEmailParser extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val sendPhoneRequest = JsonParser.fromJson[SendCodeToEmailRequest](request.contentString)
    val registerByPhoneRequest = SendCodeToEmailRequest(sendPhoneRequest.email, sendPhoneRequest.tokenCaptcha)
    DataRequestContext.setDataRequest(request, registerByPhoneRequest)
    service(request)
  }
}