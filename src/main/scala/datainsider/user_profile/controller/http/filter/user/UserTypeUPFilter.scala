package datainsider.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.parser.DataRequestContext._

import datainsider.user_profile.exception.NotAuthTypeUserPassError
import datainsider.user_profile.service.UserProfileService
import javax.inject.Inject
import user_caas.domain.thrift.Constants

/**
 * @author anhlt
 */

trait UsernameFilterRequest {
  def getUsername: String
}

class UserTypeUPFilter @Inject()(userProfileService: UserProfileService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val usernameRequest: UsernameFilterRequest = request.requestData
    userProfileService.getUserProfile(usernameRequest.getUsername).flatMap({
      case Some(x) if x.oauthType.isEmpty || x.oauthType.get.equals(Constants.OAUTH_U_P) => service(request)
      case Some(x) => Future.exception(new NotAuthTypeUserPassError)
      //      case _ => Future.exception(new Exception("username not exist"))
      case _ => service(request)
    })
  }
}