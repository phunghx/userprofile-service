package tf.user_profile.controller.http

import java.util.concurrent.TimeUnit

import com.twitter.finagle.http.{Cookie, Request}
import com.twitter.finatra.http.Controller
import com.twitter.inject.Logging
import com.twitter.util.{Duration, Future}
import datainsider.user_profile.controller.http.filter.email.EmailMustExistFilter
import tf.user_profile.controller.http.filter.parser.DataRequestContext._
import datainsider.user_profile.controller.http.filter.parser._
import tf.user_profile.controller.http.filter.user.UserContext._
import datainsider.user_profile.domain.request.SendCodeToEmailBodyRequest
import datainsider.user_profile.exception.NotFoundError
import datainsider.user_profile.service.AuthService
import javax.inject.Inject
import tf.user_profile.controller.http.filter.common.SessionFilter
import tf.user_profile.controller.http.filter.email.{EmailMustExistFilter, EmailShouldNotExistFilter}
import tf.user_profile.controller.http.filter.parser.{ForgetPasswordParser, LoginOAuthRequest, RegisterUserParser, ResetPasswordRequest, SendCodeToEmailParser, UserLoginOAuthParser, UserLoginParser, UserRegisterRequest}
import tf.user_profile.domain.request.{LoginByUserPassRequest, SendCodeToEmailBodyRequest, VerifyCodeEmailRequest}
import tf.user_profile.exception.{NotFoundError, UnAuthenticatedError}
import tf.user_profile.service.{AuthService, UserProfileService}

/**
  * @author anhlt
  */
class AuthController @Inject()(authService: AuthService,
                               profileService: UserProfileService) extends Controller with Logging {

  private val apiPath = "/user/auth"


  filter[RegisterUserParser]
    .filter[EmailShouldNotExistFilter]
    .post(s"$apiPath/register") {
      request: Request => {
        val registerUserRequest: UserRegisterRequest = request.requestData
        authService.register(registerUserRequest)
      }
    }

  filter[RegisterUserParser]
    .filter[EmailShouldNotExistFilter]
    .post(s"$apiPath/register_and_login") {
      request: Request => {
        val registerUserRequest: UserRegisterRequest = request.requestData
        authService.fastRegister(registerUserRequest)
      }
    }


  filter[SendCodeToEmailParser]
    .filter[EmailMustExistFilter]
    .post(s"$apiPath/verify_code/send") {
      request: SendCodeToEmailBodyRequest => {
        authService.getCode(request.email)
      }
    }

  filter[SendCodeToEmailParser]
    .filter[EmailMustExistFilter]
    .post(s"$apiPath/verify_code") {
      request: VerifyCodeEmailRequest => {
        authService.verifyCode(request.email, request.verifyCode)
      }
    }


  get(s"$apiPath/check_session") {
    request: Request => {
      request.user.userInfo match {
        case Some(userInfo) => profileService.getUserProfile(userInfo.username).flatMap({
          case Some(profile) => Future.value(
            authService.buildUserAuthInfoResponse(request.user.sessionId.get,
              userInfo,
              profile)
          )
          case _ => Future.exception(NotFoundError(Some("Profile not found.")))
        })
        case _ => Future.exception(UnAuthenticatedError(Some("the session is expired or invalid.")))
      }
    }
  }


  filter[UserLoginParser]
    .post(s"$apiPath/login") {
      request: Request => {
        val loginRequest: LoginByUserPassRequest = request.requestData
        authService.login(loginRequest)
      }
    }

  filter[UserLoginOAuthParser]
    .post(s"$apiPath/login_oauth") {
      request: Request => {
        val loginOAuthReq: LoginOAuthRequest = request.requestData
        authService.loginWithOAuth(loginOAuthReq)
      }
    }

  filter[SessionFilter]
    .post(s"$apiPath/logout") {
      request: Request => {
        request.user.sessionId match {
          case Some(x) => authService.logout(x)
          case _ => Future.exception(UnAuthenticatedError(Some("the session is expired or invalid.")))
        }
      }
    }

  filter[SendCodeToEmailParser]
    .filter[EmailMustExistFilter]
    .post(s"$apiPath/forgot_password") {
      request: SendCodeToEmailBodyRequest => {
        authService.beginForgetPassword(request.email)
      }
    }

  filter[SendCodeToEmailParser]
    .filter[EmailMustExistFilter]
    .post(s"$apiPath/forgot_password/verify_code") {
      request: VerifyCodeEmailRequest => {
        authService.verifyCodeForgetPassword(request.email, request.verifyCode)
      }
    }

  filter[ForgetPasswordParser]
    .filter[EmailMustExistFilter]
    .post(s"$apiPath/forgot_password/change") {
      request: Request => {
        val forgetPassRequest: ResetPasswordRequest = request.requestData
        authService.resetPassword(forgetPassRequest)
      }
    }
}