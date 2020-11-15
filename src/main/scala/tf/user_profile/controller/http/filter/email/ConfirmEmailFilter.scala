package tf.user_profile.controller.http.filter.email

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.controller.http.filter.parser.DataRequestContext._
import datainsider.user_profile.exception.EmailNotExistedError
import javax.inject.Inject
import tf.user_profile.exception.{EmailNotExistedError, EmailVerificationRequiredError}
import tf.user_profile.service.UserProfileService
import tf.user_profile.service.verification.VerifyService

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

