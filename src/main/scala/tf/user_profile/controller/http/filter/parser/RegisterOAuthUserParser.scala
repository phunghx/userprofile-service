package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.inject.Logging
import com.twitter.util.Future
import tf.user_profile.domain.Implicits._
import tf.user_profile.repository.FacebookOAuthRepository
import tf.user_profile.util.Utils
import javax.inject.Inject
import tf.user_profile.controller.http.filter.email.EmailFilterRequest
import tf.user_profile.controller.http.filter.phone.PhoneFilterRequest
import tf.user_profile.domain.request.RegisterOAuthUserBodyRequest
import tf.user_profile.repository.{FacebookOAuthRepository, GoogleOAuthRepository, OAuthRepository}
import tf.user_profile.service.verification.VerifyService
import tf.user_profile.util.{Configs, JsonParser, Utils}
import user_caas.domain.thrift.Constants

/**
 * @author anhlt
 */

case class RegisterOAuthUserRequest(
  oauthType: String,
  id: String,
  token: String,
  oauthInfo: OAuthRepository,
  password: String) extends EmailFilterRequest with PhoneFilterRequest {
  override def getEmail(): String = oauthInfo.getEmail.get

  override def getPhone(): Option[String] = oauthInfo.getPhoneNumber
}

class RegisterOAuthUserParser @Inject()(phoneAccountService: VerifyService) extends SimpleFilter[Request, Response] with Logging {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val bodyRequest = JsonParser.fromJson[RegisterOAuthUserBodyRequest](request.contentString)

    bodyRequest.phoneToken match {
      case Some(phoneToken) => phoneAccountService.verifyEmailToken(phoneToken, delete = false)
        .flatMap(phoneNumber => parseWithPhoneNumber(phoneNumber, bodyRequest, request, service))
        .flatMap(resp => phoneAccountService.deleteEmailToken(phoneToken).onFailure(fn => error("", fn)).map(_ => resp))
      case _ => phoneAccountService.verifyCode(bodyRequest.normalizedPhoneNumber.get, bodyRequest.verifyCode.get, delete = false)
        .flatMap(_ => parseWithPhoneNumber(bodyRequest.normalizedPhoneNumber.get, bodyRequest, request, service))
        .flatMap(resp => phoneAccountService.deleteVerifyCode(bodyRequest.normalizedPhoneNumber.get).onFailure(fn => error("", fn)).map(_ => resp))
    }
  }

  private def parseWithPhoneNumber(phoneNumber: String, bodyRequest: RegisterOAuthUserBodyRequest,
    request: Request, service: Service[Request, Response]): Future[Response] = {

    // check restrict for oauth type
    val restrictEmail = Configs.getRestrictEmail(bodyRequest.oauthType, "")
    if (restrictEmail.equals("-")) Future.exception(new UnsupportedOperationException(s"Unsupported oauthType=${bodyRequest.oauthType}"))

    // get oauth info
    val oauthUserInfo = bodyRequest.oauthType match {
      case Constants.OAUTH_GOOGLE => futurePool(GoogleOAuthRepository(bodyRequest.id, bodyRequest.token))
      case Constants.OAUTH_FACEBOOK => futurePool(FacebookOAuthRepository(bodyRequest.id, bodyRequest.token))
      case _ => Future.exception(new UnsupportedOperationException(s"Unsupported oauthType=${bodyRequest.oauthType}"))
    }
    // valid oauth data
    oauthUserInfo.map(oauthInfo => {
      if (!restrictEmail.isEmpty) {
        oauthInfo.getEmail match {
          case Some(x) if !Utils.isValidEmail(x, restrictEmail) => throw new UnsupportedOperationException("Unsupported your email domain")
          case Some(_) =>
          case _ => throw new Exception("email is empty")
        }
      }
      oauthInfo.setPhoneNumber(phoneNumber)

      val registerUserOAuthRequest = RegisterOAuthUserRequest(bodyRequest.oauthType, bodyRequest.id, bodyRequest.token, oauthInfo, bodyRequest.password)
      DataRequestContext.setDataRequest(request, registerUserOAuthRequest)
    }).flatMap(_ => service(request))
  }
}


