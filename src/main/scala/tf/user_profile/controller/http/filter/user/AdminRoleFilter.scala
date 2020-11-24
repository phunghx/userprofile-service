package tf.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import UserContext._
import tf.user_profile.exception.InvalidCredentialError
import javax.inject.Inject
import tf.user_profile.domain.profile.Roles
import tf.user_profile.exception.{InvalidCredentialError, UnAuthorizedError}
import tf.user_profile.service.CaasService

/**
 * @author anhlt
 */
class AdminRoleFilter @Inject()(caasService: CaasService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.user.sessionId match {
      case Some(ssid) => caasService.getUserWithSessionId(ssid).flatMap({
        case Some(userInfo) => userInfo.roles.count(role => Roles.SADMIN_ADMIN_IDS.contains(role.id)) match {
          case x if x > 0 => service(request)
          case _ => Future.exception(UnAuthorizedError(Some("permission denied")))
        }
        case _ => Future.exception(InvalidCredentialError(Some("the session is not exist")))
      })
      case _ => Future.exception(InvalidCredentialError(Some("the session is invalid.")))
    }
  }
}