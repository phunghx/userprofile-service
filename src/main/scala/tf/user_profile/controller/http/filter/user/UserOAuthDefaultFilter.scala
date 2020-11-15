package tf.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import UserContext._
import javax.inject.Inject
import tf.user_profile.exception.UnAuthorizedError
import tf.user_profile.service.AuthService

/**
 * @author anhlt
 */
class UserOAuthDefaultFilter @Inject()(authenService: AuthService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    authenService.isUserOAuthDefault(request.user.username.get).map({
      case true => service(request)
      case _ => Future.exception(UnAuthorizedError(Some("Not allow")))
    }).flatten
  }
}
