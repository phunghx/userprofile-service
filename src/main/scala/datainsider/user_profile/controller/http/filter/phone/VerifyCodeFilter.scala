package datainsider.user_profile.controller.http.filter.phone

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.parser.DataRequestContext._
import datainsider.user_profile.service.verification.VerifyService
import javax.inject.Inject

/**
 * @author anhlt
 */
trait VerifyCodeFilterRequest {
  def getPhone(): String

  def getVerifyCode(): String
}

class VerifyCodeFilter @Inject()(verifyService: VerifyService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {

    val bodyReq: VerifyCodeFilterRequest = request.requestData
    verifyService.verifyCode(bodyReq.getPhone(), bodyReq.getVerifyCode(), true).flatMap(f => service(request))
  }
}