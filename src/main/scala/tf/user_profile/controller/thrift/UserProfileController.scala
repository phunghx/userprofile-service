package tf.user_profile.controller.thrift

import com.google.inject.Inject
import com.twitter.finagle.Service
import com.twitter.finatra.thrift.Controller
import com.twitter.inject.Logging
import com.twitter.scrooge.{Request, Response}
import com.twitter.util.Future
import tf.user_profile.domain.Implicits._
import tf.user_profile.domain.ThriftImplicit._
import datainsider.user_profile.service.AuthService
import datainsider.user_profile.domain.thrift._
import tf.user_profile.domain.Pageable
import tf.user_profile.service.TUserProfileService._
import tf.user_profile.domain.profile.UserAuthInfoResponse
import tf.user_profile.domain.thrift.{TFullUserAuthInfoResp, TFullUserInfoResp, TMultiUserProfileResp, TUserProfileResp, TUserProfileSearchResp}
import tf.user_profile.service.{AuthService, CaasService, TUserProfileService, UserProfileService}

/**
 * @author anhlt
 */
class UserProfileController @Inject()(authService: AuthService,
                                      profileService: UserProfileService,
                                      caasService: CaasService) extends Controller(TUserProfileService) with Logging {

  handle(Ping).withFn {
    _: Request[Ping.Args] =>
      async {
        Response(value = "Pong")
      }
  }


  handle(CheckSession).withFn {
    request: Request[CheckSession.Args] => {
      authService.checkSession(request.args.sessionId).rescue {
        case e: Throwable => futurePool(UserAuthInfoResponse(null, null))
      }.map(f => f.session match {
        case null =>
          TFullUserAuthInfoResp(false).toScroogeResponse()
        case _ =>
          TFullUserAuthInfoResp(exist = true,
            session = Some(f.session),
            userInfo = Some(f.userInfo),
            userProfile = f.userProfile).toScroogeResponse()
      })
    }
  }

  handle(GetUserProfileBySessionID).withFn {
    request: Request[GetUserProfileBySessionID.Args] => {
      caasService.getUserWithSessionId(request.args.sessionId).flatMap({
        case Some(userInfo) =>
          profileService.getUserProfile(userInfo.username).map(userProfile => TFullUserInfoResp(true, Some(userInfo), userProfile))
        case _ => Future.value(TFullUserInfoResp(false))
      }).map(_.toScroogeResponse())
    }
  }

  handle(GetUserProfile).withFn {
    request: Request[GetUserProfile.Args] => {
      profileService.getUserProfile(request.args.username).map({
        case Some(x) => TUserProfileResp(true, Some(x))
        case _ => TUserProfileResp(false)
      }).map(_.toScroogeResponse())
    }
  }

  handle(MultiGetUserProfiles).withFn {
    request: Request[MultiGetUserProfiles.Args] => {
      profileService.getUserProfiles(request.args.usernames.toSeq).map(r => {
        TMultiUserProfileResp(
          total = r.size,
          userProfiles = Some(r.map(e => e._1 -> UserProfile2T(e._2)))
        )
      }).map(_.toScroogeResponse())
    }
  }

  handle(GetUserProfileByUsername).withFn {
    request: Request[GetUserProfileByUsername.Args] => {
      caasService.getUserInfo(request.args.username).flatMap({
        case Some(userInfo) => profileService.getUserProfile(userInfo.username)
          .map(userProfile => TFullUserInfoResp(true, Some(userInfo), userProfile))
        case _ => Future.value(TFullUserInfoResp(false))
      }).map(_.toScroogeResponse())
    }
  }

  handle(GetProfileByEmail).withFn {
    request: Request[GetProfileByEmail.Args] => {
      profileService.getUserProfileByEmail(request.args.email).map({
        case Some(profile) => TUserProfileResp(true, Some(profile))
        case _ => TUserProfileResp(false)
      }).map(_.toScroogeResponse())
    }
  }


  handle(GetUserProfiles).withFn {
    request: Request[GetUserProfiles.Args] => {
      profileService.getActiveUserProfiles(request.args.from, request.args.size)
        .rescue { case e => Future.value(Pageable(0)) }
        .map(f => TUserProfileSearchResp(f.total, Option(f.data)))
        .map(_.toScroogeResponse())
    }
  }
}
