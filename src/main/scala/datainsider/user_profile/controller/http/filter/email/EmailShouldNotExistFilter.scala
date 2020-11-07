package datainsider.user_profile.controller.http.filter.email

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.parser.DataRequestContext._
import datainsider.user_profile.exception.{EmailExistedError, EmailNotExistedError}
import datainsider.user_profile.service.UserProfileService
import javax.inject.Inject

/**
  * @author anhlt
  */

trait EmailFilterRequest {
  def getEmail(): String
}

class EmailShouldNotExistFilter @Inject()(profileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val emailFilterRequest: EmailFilterRequest = request.requestData

    profileService.isExistEmail(emailFilterRequest.getEmail()).flatMap({
      case false => service(request)
      case _ => Future.exception(new EmailExistedError)
    })
  }
}

class EmailMustExistFilter @Inject()(userProfileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val emailRequest: EmailFilterRequest = request.requestData
    userProfileService.isExistEmail(emailRequest.getEmail()).flatMap({
      case false => Future.exception(new EmailNotExistedError)
      case _ => service(request)
    })
  }
}

