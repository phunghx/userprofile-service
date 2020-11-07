package datainsider.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.inject.Logging
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.email.EmailFilterRequest
import datainsider.user_profile.domain.request.ForgetPasswordBodyRequest
import datainsider.user_profile.service.verification.VerifyService
import datainsider.user_profile.util.JsonParser
import javax.inject.Inject

/**
  * @author anhlt
  */
case class ResetPasswordRequest(email: String, newPassword: String) extends EmailFilterRequest {
  override def getEmail(): String = email
}

class ForgetPasswordParser @Inject()(verifyService: VerifyService) extends SimpleFilter[Request, Response] with Logging {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val bodyRequest = JsonParser.fromJson[ForgetPasswordBodyRequest](request.contentString)
    for {
      email <- verifyService.verifyEmailToken(bodyRequest.emailToken, delete = false)
      _ = DataRequestContext.setDataRequest(request, ResetPasswordRequest(bodyRequest.email, bodyRequest.newPassword))
      resp <- service(request)
      _ <- verifyService.deleteEmailToken(bodyRequest.emailToken).rescue { case e: Exception => Future.value(error("", e)) }
    } yield resp
  }
}