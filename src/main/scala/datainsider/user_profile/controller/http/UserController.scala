package datainsider.user_profile.controller.http

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.inject.Logging
import datainsider.user_profile.controller.http.filter.parser.{EditUserProfileRequest, EditUserSettingRequest, MultiGetUserProfileRequest}
import datainsider.user_profile.controller.http.filter.user.UserContext._
import datainsider.user_profile.controller.http.filter.user.{AdminRoleFilter, UserSignedInFilter}
import datainsider.user_profile.domain.request.SetRoleUserRequest
import datainsider.user_profile.service.{ AuthorizationService, UserProfileService}
import datainsider.user_profile.util.Utils
import javax.inject.Inject

/**
 * @author anhlt
 */
class UserController @Inject()(authorizationService: AuthorizationService,
                               profileService: UserProfileService) extends Controller with Logging {

  get("/user/profile/email/:email") {
    request: Request => {
      profileService.getUserProfileByEmail(request.getParam("email"))
    }
  }

  filter[UserSignedInFilter]
    .get("/user/profile/me") {
      request: Request => {
        profileService.getUserProfile(request.user.username.get)
          .map(Utils.throwIfNotExist(_, Some("this profile is not found.")))
      }
    }


  filter[UserSignedInFilter]
    .put("/user/profile/me") {
      request: EditUserProfileRequest => {
        profileService.editUserProfile(request)
      }
    }

  filter[UserSignedInFilter]
    .put("/user/profile/setting/me") {
      request: EditUserSettingRequest => {
        profileService.editUserSettings(request)
      }
    }

  filter[UserSignedInFilter]
    .get("/user/profile/:username") {
      request: Request => {
        val userId = request.getParam("username")
        profileService.getUserProfile(userId).map(Utils.throwIfNotExist(_, Some("this profile is not found.")))
      }
    }

  filter[UserSignedInFilter]
    .post("/user/profile/list") {
      request: MultiGetUserProfileRequest => {
        profileService.getUserProfiles(request.usernames)
      }
    }


  filter[AdminRoleFilter]
    .post("/user/role") {
      request: SetRoleUserRequest => {
        authorizationService.updateRoles(request.username, request.oldRoleIds, request.newRoleIds)
      }
    }


}
