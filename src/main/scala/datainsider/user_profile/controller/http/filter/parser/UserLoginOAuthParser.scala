package datainsider.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.domain.Implicits._
import datainsider.user_profile.domain.request.UserOAuthBodyRequest
import datainsider.user_profile.exception.RegistrationRequiredError
import datainsider.user_profile.repository.{FacebookOAuthRepository, GoogleOAuthRepository, OAuthRepository}
import datainsider.user_profile.service.UserProfileService
import datainsider.user_profile.util.{Configs,  JsonParser, Utils}
import javax.inject.Inject
import user_caas.domain.thrift.Constants

/**
 * @author anhlt
 */

case class LoginOAuthRequest(oauthType: String,
                             id: String,
                             token: String,
                             oauthInfo: OAuthRepository)

class UserLoginOAuthParser @Inject()(profileService: UserProfileService) extends SimpleFilter[Request, Response] {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {

    val oAuthBodyRequest = JsonParser.fromJson[UserOAuthBodyRequest](request.contentString)

    // check restrict for oauth type

    for {
      // get oauth info
      // valid oauth data
      oauthInfo <- oAuthBodyRequest.oauthType match {
        case Constants.OAUTH_GOOGLE => futurePool(GoogleOAuthRepository(oAuthBodyRequest.id, oAuthBodyRequest.token))
        case Constants.OAUTH_FACEBOOK => futurePool(FacebookOAuthRepository(oAuthBodyRequest.id, oAuthBodyRequest.token))
        case _ => Future.exception(new UnsupportedOperationException(s"Unsupported oauthType=${oAuthBodyRequest.oauthType}"))
      }
      _ = verifyRestrictedEmail(oauthInfo)
      oauthInfo <- Configs.isNeedVerifyPhone(oauthInfo.oauthType) match {
        case true => profileService.getUserProfile(oauthInfo.getUsername).map({
          case Some(profile) => oauthInfo
          case _ => throw new RegistrationRequiredError
        })
        case _ => futurePool(oauthInfo)
      }
      _ = DataRequestContext.setDataRequest(request, LoginOAuthRequest(oAuthBodyRequest.oauthType, oAuthBodyRequest.id, oAuthBodyRequest.token, oauthInfo))
      resp <- service(request)
    } yield {
      resp
    }
  }


  private def verifyRestrictedEmail(oAuthInfo : OAuthRepository) = {
    val restrictedEmailPattern = Configs.getRestrictEmail(oAuthInfo.oauthType, "")
    if (restrictedEmailPattern.equals("-"))
      throw new UnsupportedOperationException(s"Unsupported oauthType=${oAuthInfo.oauthType}")

    if (restrictedEmailPattern.nonEmpty) {
      oAuthInfo.getEmail match {
        case Some(x) if !Utils.isValidEmail(x, restrictedEmailPattern) =>
          throw new UnsupportedOperationException("Unsupported your email domain")
        case Some(_) =>
        case _ => throw new Exception("email is empty")
      }
    }
  }
}
