package datainsider.user_profile.controller.http.filter.email

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.parser.DataRequestContext._
import datainsider.user_profile.exception.{EmailNotExistedError, EmailVerificationRequiredError}
import datainsider.user_profile.service.UserProfileService
import datainsider.user_profile.service.verification.VerifyService
import javax.inject.Inject

/**
  * @author anhlt
  */

trait ConfirmEmailFilterRequest {
  def getUsername(): String
}

class ConfirmEmailFilter @Inject()(profileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val confirmEmailFilterRequest: ConfirmEmailFilterRequest = request.requestData
    profileService.isConfirmEmail(confirmEmailFilterRequest.getUsername()).flatMap({
      case false => Future.exception(new EmailVerificationRequiredError)
      case _ => service(request)
    })
  }
}

class NotConfirmEmailFilter @Inject()(verifyService: VerifyService, profileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val emailRequest: ConfirmEmailFilterRequest = request.requestData
    profileService.isConfirmEmail(emailRequest.getUsername()).flatMap({
      case false => service(request)
      case _ => Future.exception(new EmailNotExistedError)
    })
  }
}

