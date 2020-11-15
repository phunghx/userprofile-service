package tf.user_profile.service

import com.twitter.inject.Logging
import com.twitter.util.{Future, Return, Throw}
import javax.inject.Inject
import tf.user_profile.domain.profile.Roles
import tf.user_profile.exception.InternalError

/**
 * @author anhlt
 */
trait AuthorizationService {

  def updateRoles(username: String, oldRoleIds: Set[Int], newRoleIds: Map[Int, Long]): Future[Boolean]

  def resetPassword(username: String, newPassword: String): Future[Unit]
}

class AuthorizationServiceImpl @Inject()(caasService: CaasService,
                                         profileService: UserProfileService) extends AuthorizationService with Logging {

  override def updateRoles(username: String, oldRoleIds: Set[Int], newRoleIds: Map[Int, Long]): Future[Boolean] = {
    val fn = for {
      _ <- if(oldRoleIds.nonEmpty) caasService.deleteUserRoles(username, oldRoleIds) else Future.value({})
      r <- caasService.insertUserRoles(username, newRoleIds)
    } yield r

    fn.transform({
      case Return(r) => Future.True
      case Throw(e) => Future.exception(InternalError(Some(s"Can't update new roles for this user: ${e.getMessage}")))
    })
  }

  override def resetPassword(username: String, newPassword: String): Future[Unit] = {
    caasService.resetPasswordUser(username, newPassword)
  }

}