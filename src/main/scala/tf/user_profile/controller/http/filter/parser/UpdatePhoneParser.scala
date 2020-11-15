package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.inject.Logging
import com.twitter.util.Future
import datainsider.user_profile.util.JsonParser
import javax.inject.Inject
import tf.user_profile.controller.http.filter.phone.PhoneFilterRequest
import tf.user_profile.domain.request.UpdatePhoneBodyRequest
import tf.user_profile.service.verification.VerifyService
import tf.user_profile.util.JsonParser

/**
 * @author anhlt
 */

case class UpdatePhoneRequest(phoneNumber: String) extends PhoneFilterRequest {
  override def getPhone(): Option[String] = Some(phoneNumber)
}

class UpdatePhoneParser @Inject()(verifyService: VerifyService) extends SimpleFilter[Request, Response] with Logging {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val bodyRequest = JsonParser.fromJson[UpdatePhoneBodyRequest](request.contentString)
    for {
      _ <- verifyService.verifyCode(bodyRequest.normalizedPhoneNumber, bodyRequest.verifyCode, delete = false)
      _ = DataRequestContext.setDataRequest(request, UpdatePhoneRequest(bodyRequest.normalizedPhoneNumber))
      resp <- service(request)
      _ <- verifyService.deleteVerifyCode(bodyRequest.normalizedPhoneNumber).rescue { case e: Exception => Future.value(error("", e)) }
    } yield resp
  }
}