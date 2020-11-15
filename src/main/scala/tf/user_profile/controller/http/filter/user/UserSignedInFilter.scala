package tf.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import UserContext._
import javax.inject.Inject
import tf.user_profile.exception.InvalidCredentialError
import tf.user_profile.service.CaasService

/**
 * @author anhlt
 */
trait UserSignedInRequest {
  val request: Request

  def getUsername(): String = request.user.username.get
}

class UserSignedInFilter @Inject()(caasService: CaasService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    if (request.user.sessionId.isEmpty || request.user.username.isEmpty)
      throw InvalidCredentialError(Some("the session is invalid."))
    service(request)
  }
}