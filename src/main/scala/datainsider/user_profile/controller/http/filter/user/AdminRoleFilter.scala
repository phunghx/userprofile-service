package datainsider.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.user.UserContext._
import datainsider.user_profile.domain.profile.Roles
import datainsider.user_profile.exception.{InvalidCredentialError, UnAuthorizedError}
import datainsider.user_profile.service.CaasService
import javax.inject.Inject

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