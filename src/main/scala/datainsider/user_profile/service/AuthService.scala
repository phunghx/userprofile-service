package datainsider.user_profile.service

import com.twitter.inject.Logging
import com.twitter.util.{Future, Return, Throw}
import datainsider.user_profile.controller.http.filter.parser.{LoginOAuthRequest, ResetPasswordRequest, UserRegisterRequest}
import datainsider.user_profile.domain.Implicits._
import datainsider.user_profile.domain.ThriftImplicit._
import datainsider.user_profile.domain.profile._
import datainsider.user_profile.domain.request.{LoginByEmailRequest, LoginByUserPassRequest}
import datainsider.user_profile.exception._
import datainsider.user_profile.module.BaseResponse
import datainsider.user_profile.repository.OAuthRepository
import datainsider.user_profile.service.verification.VerifyService
import datainsider.user_profile.util.{Configs, JsonParser, ZConfig}
import javax.inject.Inject
import user_caas.domain.thrift.{Constants, TUserAuthInfo, TUserInfo}

/**
 * @author anhlt
 */
trait AuthService {

  def buildUserAuthInfoResponse(ssid: String, userInfo: UserInfo, userProfile: UserProfile): UserAuthInfoResponse

  def verifyCode(email: String, verifyCode: String): Future[UserAuthInfoResponse]

  def getCode(phoneNumber: String): Future[Boolean]

  def register(request: UserRegisterRequest): Future[UserFullInfo]

  def fastRegister(request: UserRegisterRequest): Future[UserAuthInfoResponse]

  def login(request: LoginByUserPassRequest): Future[UserAuthInfoResponse]

  def loginWithEmail(request: LoginByEmailRequest): Future[UserAuthInfoResponse]

  def loginWithOAuth(request: LoginOAuthRequest): Future[UserAuthInfoResponse]

  def logout(ssId: String): Future[SessionInfo]

  def checkSession(ssId: String): Future[UserAuthInfoResponse]


  def isUserOAuthDefault(username: String): Future[Boolean]

  def beginForgetPassword(email: String): Future[Boolean]

  def verifyCodeForgetPassword(email: String, verifyCode: String): Future[BaseResponse]

  def resetPassword(resetPassRequest: ResetPasswordRequest): Future[UserAuthInfoResponse]
}

class AuthServiceImpl @Inject()(verifyService: VerifyService,
                                caasService: CaasService,
                                profileService: UserProfileService) extends AuthService with Logging {

  private val sessionTimeoutInMS = ZConfig.getLong("session.timeout_in_ms")
  private val sessionDomain = ZConfig.getString("session.domain")
  private val sessionKey = ZConfig.getString("session.name")
  private val userCreatedBeforeTime = ZConfig.getLong("caas.user_created_before_time", -1)

  override def verifyCode(email: String, verifyCode: String): Future[UserAuthInfoResponse] = {
    for {
      _ <- verifyService.verifyCode(email, verifyCode, true)
      loginResult <- profileService.getUserIdByEmail(email).flatMap({
        case Some(x) => caasService.loginOAuth(x, sessionTimeoutInMS)
        case _ => Future.exception(EmailNotExistedError(s"the email ($email) not exist"))
      })
      userProfile = UserProfile(
        username = loginResult.userInfo.username,
        email = email,
        alreadyConfirmed = true
      )
      userProfile <- profileService.getOrAddUserProfile(loginResult.userInfo.username, userProfile, true)
      sessionInfo = SessionInfo(sessionKey, loginResult.ssid, sessionDomain, sessionTimeoutInMS)
    } yield {
      UserAuthInfoResponse(sessionInfo, loginResult.userInfo, userProfile)
    }
  }

  private def _register(request: UserRegisterRequest, needVerification: Boolean = true) = {

    val userProfile = request.build(isConfirmed = Some(!needVerification))
    val username = userProfile.username
    val fn = for {
      userInfo <- caasService.registerUser(username, request.password)
      userProfile <- profileService.getOrAddUserProfile(userInfo.username, userProfile, true)
      _ <- if (userProfile.alreadyConfirmed) Future.True else {
        verifyService.genAndSendVerifyCode(request.email)
      }
    } yield {
      info(JsonParser.toJson(userProfile))
      UserFullInfo(userInfo, Some(userProfile))
    }

    fn.transform({
      case Return(r) => Future.value(r)
      case Throw(e) =>
        caasService.deleteUser(username)
        profileService.deleteUserProfile(username)
        Future.exception(e)
    })
  }


  override def register(request: UserRegisterRequest): Future[UserFullInfo] = {
    _register(request, needVerification = true)
  }


  override def fastRegister(request: UserRegisterRequest): Future[UserAuthInfoResponse] = {
    for {
      userFullInfo <- _register(request, needVerification = false)
      loginRequest = LoginByUserPassRequest(userFullInfo.userInfo.username,
        request.password,
        true)
      r <- login(loginRequest)
    } yield {
      r
    }
  }


  override def loginWithOAuth(request: LoginOAuthRequest): Future[UserAuthInfoResponse] = {
    for {
      oldProfile <- findUserIfExist(request.oauthInfo)
      loginResult <- oldProfile match {
        case Some(profile) => caasService.loginOAuth(profile.username, sessionTimeoutInMS)
        case _ => caasService.loginWithOAuth(request.oauthType, request.id, request.token, sessionTimeoutInMS)
      }

      userProfile <- if (oldProfile.isDefined) Future.value(oldProfile.get) else profileService.getOrAddUserProfile(
        loginResult.userInfo.username,
        UserOAuth2Profile(request.oauthInfo, loginResult.userInfo.username),
        Configs.enableAutoUpdateProfile(request.oauthType)
      )
      isDefaultOAuthCredential <- isDefaultOAuthCredential(loginResult.userInfo.username, loginResult.userInfo.createTime)

      sessionInfo = SessionInfo(sessionKey, loginResult.ssid, sessionDomain, sessionTimeoutInMS)
    } yield {
      UserAuthInfoResponse(sessionInfo,
        loginResult.userInfo,
        Some(userProfile),
        defaultOAuthCredential = isDefaultOAuthCredential)
    }
  }


  override def login(request: LoginByUserPassRequest): Future[UserAuthInfoResponse] = {
    for {
      loginResult <- caasService.login(request.username, request.password, sessionTimeoutInMS)
      profile <- profileService.getUserProfile(loginResult.userInfo.username).flatMap({
        case Some(profile) if !profile.alreadyConfirmed =>
          caasService.logout(loginResult.ssid).transform({
            case Return(r) => Future.True
            case Throw(e) => Future.True
          }).flatMap(_ => Future.exception(new EmailVerificationRequiredError))
        case x => Future.value(x)
      })

    } yield {
      val sessionInfo = SessionInfo(sessionKey,
        loginResult.ssid,
        sessionDomain,
        sessionTimeoutInMS)
      UserAuthInfoResponse(sessionInfo, loginResult.userInfo, profile)
    }

  }

  override def loginWithEmail(request: LoginByEmailRequest): Future[UserAuthInfoResponse] = {
    profileService.getUserIdByEmail(request.email).flatMap({
      case Some(x) => login(LoginByUserPassRequest(x, request.password, request.remember))
      case _ => Future.exception(new Exception("email not exist"))
    })
  }



  private def findUserIfExist(oauthRepository: OAuthRepository) : Future[Option[UserProfile]] = {
    oauthRepository.getEmail match {
      case Some(email) => profileService.getUserProfileByEmail(email).flatMap({
        case Some(x) => Future.value(Some(x))
        case _ => profileService.getUserProfile(oauthRepository.getUsername)
      })
      case _ => profileService.getUserProfile(oauthRepository.getUsername)
    }
  }

  private def isDefaultOAuthCredential(username: String, createdTime: Long): Future[Option[Boolean]] = {
    if (createdTime < userCreatedBeforeTime) {
      isUserOAuthDefault(username).rescue { case e: Exception => Future.value(false) }.map(f => Some(f))
    } else Future.value(None)
  }

  override def isUserOAuthDefault(username: String): Future[Boolean] = {
    profileService.getUserProfile(username).flatMap({
      case Some(x) => x.oauthType.getOrElse("") match {
        case Constants.OAUTH_FACEBOOK | Constants.OAUTH_GOOGLE =>
          caasService.isCredentialDefault(x.oauthType.get, username)
        case _ => Future.value(false)
      }
      case _ => Future.value(false)
    })
  }

  override def logout(ssId: String): Future[SessionInfo] = caasService.logout(ssId).map(f => SessionInfo(sessionKey, ssId, sessionDomain, -1))

  override def checkSession(ssId: String): Future[UserAuthInfoResponse] = {
    for {
      userInfo <- caasService.getUserWithSessionId(ssId)
      profile <- userInfo match {
        case Some(userInfo) => profileService.getUserProfile(userInfo.username)
        case _ => Future.exception(UnAuthenticatedError("the session is expired or not exist"))
      }
    } yield profile match {
      case Some(profile) =>
        UserAuthInfoResponse(session = SessionInfo(sessionKey,
          ssId,
          sessionDomain,
          sessionTimeoutInMS),
          userInfo.get,
          profile)
      case _ => throw NotFoundError(Some("this profile is not found."))
    }

  }

  def buildUserAuthInfoResponse(ssid: String, userInfo: UserInfo, profile: UserProfile) = {
    UserAuthInfoResponse(session = SessionInfo(sessionKey,
      ssid,
      sessionDomain,
      sessionTimeoutInMS),
      userInfo,
      profile)
  }

  override def getCode(email: String): Future[Boolean] = {
    verifyService.genAndSendVerifyCode(email)
  }

  override def beginForgetPassword(email: String): Future[Boolean] = {
    verifyService.genAndSendForgotPasswordCode(email)
  }

  override def verifyCodeForgetPassword(email: String, verifyCode: String): Future[BaseResponse] = {
    for {
      _ <- verifyService.verifyCode(email, verifyCode, true)
      profile <- profileService.getUserIdByEmail(email).flatMap({
        case Some(x) => profileService.getOrAddUserProfile(x, UserProfile(alreadyConfirmed = true))
        case _ => Future.exception(EmailNotExistedError("email is not exist"))
      })
      token <- verifyService.genTokenWithEmail(email)
      r = Option(token).flatMap(x => if (x == null || x.isEmpty) None else Some(x))
    } yield {
      BaseResponse(r.isDefined, r, None)
    }
  }

  override def resetPassword(resetPassRequest: ResetPasswordRequest): Future[UserAuthInfoResponse] = {

    for {
      userId <- profileService.getUserIdByEmail(resetPassRequest.email).flatMap({
        case Some(userId) => Future.value(userId)
        case _ => Future.exception(new EmailNotExistedError)
      })
      _ <- caasService.resetPasswordUser(userId, resetPassRequest.newPassword)
      loginResult <- caasService.loginOAuth(userId, sessionTimeoutInMS)

      userProfile <- {
        val userProfile = UserProfile(
          username = loginResult.userInfo.username,
          email = resetPassRequest.email
        )
        profileService.getOrAddUserProfile(loginResult.userInfo.username, userProfile)
      }

    } yield {
      val sessionInfo = SessionInfo(sessionKey,
        loginResult.ssid,
        sessionDomain,
        sessionTimeoutInMS)
      UserAuthInfoResponse(sessionInfo, loginResult.userInfo, userProfile)
    }
  }

  private def checkUserOAuthDefault(username: String): PartialFunction[Throwable, Future[TUserAuthInfo]] = {
    case e: Exception => isUserOAuthDefault(username).flatMap({
      case true => Future.exception(NotAuthTypeUserPassError())
      case _ => Future.exception(e)
    })
  }

  private def insertStaffRoleIfNeeded(oauthInfo: OAuthRepository, userInfo: TUserInfo): Future[Seq[Int]] = {
    Configs.isOrgEmail(oauthInfo.oauthType, oauthInfo.getEmail) match {
      case true => caasService.insertStaffRoleIfNeeded(userInfo.username, userInfo.roles)
      case _ => futurePool(userInfo.roles.map(v => v.id))
    }
  }
}