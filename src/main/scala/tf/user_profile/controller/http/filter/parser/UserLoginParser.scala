package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.domain.request.LoginByEmailRequest
import tf.user_profile.util.JsonParser
import javax.inject.Inject
import tf.user_profile.domain.request.{LoginByEmailRequest, LoginByUserPassRequest}
import tf.user_profile.exception.InvalidCredentialError
import tf.user_profile.service.UserProfileService
import tf.user_profile.util.JsonParser

/**
  * @author anhlt
  */


class UserLoginParser @Inject()(profileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val loginByEmailReq = JsonParser.fromJson[LoginByEmailRequest](request.contentString)
    for {
      username <- profileService.getUserIdByEmail(loginByEmailReq.email).map({
        case Some(user) => user
        case _ => throw InvalidCredentialError(Some( "the credentials are invalid."))
      })
      _ = DataRequestContext.setDataRequest(request, LoginByUserPassRequest(username, loginByEmailReq.password, loginByEmailReq.remember))
      resp <- service(request)
    } yield resp
  }
}
