package tf.user_profile.service

import com.twitter.inject.Logging
import com.twitter.util.Future
import tf.user_profile.domain.Implicits._
import tf.user_profile.domain.ThriftImplicit._
import tf.user_profile.domain.profile.UserInfoPageable
import javax.inject.Inject
import tf.user_profile.domain.Pageable
import tf.user_profile.domain.profile.{Roles, UserInfoPageable}
import tf.user_profile.exception.InvalidCredentialError
import user_caas.domain.thrift.{TRoleInfo, TUserAuthInfo, TUserInfo, TUserInfoResult}
import user_caas.service.TCaasService

/**
 * Created by sonpn
 */
trait CaasService {

  def ping(): Future[String]

  def login(username: String, password: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo]

  def loginWithOAuth(oauthType: String, id: String, token: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo]

  def getUserWithSessionId(sessionId: String): Future[Option[TUserInfo]]

  def getUserInfo(username: String): Future[Option[TUserInfo]]

  def logout(sessionId: String): Future[Unit]

  def getAllUsername(): Future[Seq[String]]

  def getActiveUsername(from: Int, size: Int): Future[Pageable[String]]

  def insertUserRoles(username: String, roleIds: Map[Int, Long]): Future[Unit]

  def insertUserRoles(username: String, roleIds: Set[Int]): Future[Unit]

  def deleteUserRoles(username: String, roleIds: Set[Int]): Future[Unit]

  def insertStaffRoleIfNeeded(username: String, currentRoles: Seq[TRoleInfo]): Future[Seq[Int]]

  def registerUser(username: String, password: String): Future[TUserInfo]

  def registerUserWithOAuth(oauthType: String, id: String, token: String, password: String): Future[TUserInfo]

  def deleteUser(username: String): Future[Boolean]

  def getListUserRole(notInRoleIds: Option[Seq[Int]], inRoleIds: Option[Seq[Int]], from: Int, size: Int): Future[UserInfoPageable]

  def searchListUserRole(usernameSearchKey: String, notInRoleIds: Option[Seq[Int]], inRoleIds: Option[Seq[Int]], from: Int, size: Int): Future[UserInfoPageable]

  def resetPasswordUser(username: String, newPassword: String): Future[Unit]

  def updatePasswordUser(username: String, oldPassword: String, newPassword: String): Future[Unit]

  def isCredentialDefault(oauthType: String, username: String): Future[Boolean]

  def loginOAuth(username: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo]


  def hasRole(sessionId: String, roleName: String): Future[Option[Boolean]]

  def hasRoles(sessionId: String, roleNames: Seq[String]): Future[Option[Boolean]]

  def hasRoleUser(username: String, roleName: String): Future[Option[Boolean]]

  def hasAllRoleUser(username: String, roleNameS: Seq[String]): Future[Option[Boolean]]

  def getUserRoles(username: String): Future[Option[Seq[TRoleInfo]]]

  def getAllPermission(username: String): Future[Option[Seq[String]]]

  def deleteAllExpiredUserRole(defaultRole: Int, maxTime: Long): Future[Option[Boolean]]
}

case class CaasServiceImpl@Inject()(client: TCaasService.MethodPerEndpoint) extends CaasService with Logging {

  val STAFF_ROLE_IDS = Map(Roles.STAFF.id -> Long.MaxValue)

  def ping(): Future[String] = client.ping

  def login(username: String, password: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo] = {
    for {
      result <- client.login(username, password, Some(sessionTimeoutInMS))
    } yield {
      result.code match {
        case 0 => result.userAuthInfo.get
        case _ =>
          if (result.msg.isDefined) logger.error(result.msg.get)
          throw InvalidCredentialError("Login info wrong")
      }
    }
  }

  def loginWithOAuth(oauthType: String, id: String, token: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo] = {
    for {
      result <- client.loginWithOAuth(oauthType, id, token, Some(sessionTimeoutInMS))
    } yield {
      result.code match {
        case 0 => result.userAuthInfo.get
        case _ => result.msg match {
          case Some(x) => throw new Exception(x)
          case _ => throw new Exception("Failed to loginWithOAuth")
        }
      }
    }
  }

  def getUserWithSessionId(sessionId: String): Future[Option[TUserInfo]] = {
    client.getUserWithSessionId(sessionId).map(result => result.code match {
      case 0 => Some(result.userInfo.get)
      case _ => {
        logger.error(s"Error in getUserWithSessionId: $sessionId - ${result.msg.getOrElse("")}")
        None
      }
    })
  }

  def getUserInfo(username: String): Future[Option[TUserInfo]] = {
    client.getUserWithUsername(username).rescue {
      case e: Throwable => Future.value(TUserInfoResult(-2, msg = Some(e.getMessage)))
    }.map(result => result.code match {
      case 0 => Some(result.userInfo.get)
      case _ => {
        val msg = result.msg match {
          case Some(x) => x
          case _ => "Failed to get user info by username."
        }
        logger.error(msg)
        None
      }
    })
  }

  def logout(sessionId: String): Future[Unit] = {
    for {
      result <- client.logout(sessionId)
    } yield {
      result.code match {
        case 0 => {}
        case _ => result.msg match {
          case Some(x) => throw new Exception(x)
          case _ => throw new Exception("Failed to logout")
        }
      }
    }
  }

  def getAllUsername(): Future[Seq[String]] = {
    for {
      result <- client.getAllUsername()
    } yield {
      result.code match {
        case 0 => result.data match {
          case Some(x) => x
          case _ => List.empty
        }
        case _ => {
          val ex = result.msg match {
            case Some(x) => new Exception(x)
            case _ => new Exception("Failed to getAllUsername")
          }
          logger.error(ex.getMessage, ex)
          throw ex
        }
      }
    }
  }

  override def insertUserRoles(username: String, roleIds: Map[Int, Long]): Future[Unit] = {
    client.insertExpirableUserRoles(username, roleIds).map(result => result.code match {
      case 0 =>
      case _ => {
        var msg = "Failed to insertUserRoles"
        result.msg match {
          case Some(x) => {
            error(x)
            msg = x
          }
          case _ => error("Failed to insertUserRoles")
        }
        throw new Exception(msg)
      }
    })
  }

  override def deleteUserRoles(username: String, roleIds: Set[Int]): Future[Unit] = {
    client.deleteUserRoles(username, roleIds).map(result => result.code match {
      case 0 =>
      case _ => {
        var msg = "Failed to deleteUserRoles"
        result.msg match {
          case Some(x) =>
            logger.error(x)
            msg = x
          case _ => logger.error("Failed to deleteUserRoles")
        }
        throw new Exception(msg)
      }
    })
  }

  override def insertStaffRoleIfNeeded(username: String, currentRoles: Seq[TRoleInfo]): Future[Seq[Int]] = {
    currentRoles.size match {
      case x if x == 0 => insertUserRoles(username, STAFF_ROLE_IDS).map(f => STAFF_ROLE_IDS.keys.toSeq)
      case _ => futurePool(currentRoles.map(v => v.id))
    }
  }

  override def registerUser(username: String, password: String): Future[TUserInfo] = {
    client.register(username, password).map(result => result.code match {
      case 0 => result.userInfo.get
      case _ => result.msg match {
        case Some(x) => throw new Exception(x)
        case _ => throw new Exception("Failed to registerUser")
      }
    })
  }

  override def registerUserWithOAuth(oauthType: String, id: String, token: String, password: String): Future[TUserInfo] = {
    client.registerWithOAuth(oauthType, id, token, Option(password)).map(result => result.code match {
      case 0 => result.userInfo.get
      case _ => result.msg match {
        case Some(x) => throw new Exception(x)
        case _ => throw new Exception("Failed to registerUserWithOAuth")
      }
    })
  }

  override def deleteUser(username: String): Future[Boolean] = {
    client.deleteUser(username).map(result => result.code match {
      case 0 => true
      case _ => {
        result.msg match {
          case Some(x) => logger.error(s"Failed to delete user [$username]: ${x}")
          case _ => logger.error(s"Failed to delete user [$username]")
        }
        false
      }
    })
  }

  override def getListUserRole(notInRoleIds: Option[Seq[Int]], inRoleIds: Option[Seq[Int]], from: Int, size: Int): Future[UserInfoPageable] = {
    client.getListUserRole(notInRoleIds, inRoleIds, from, size).map(f => f.code match {
      case 0 => UserInfoPageable(f.total.get, Some(f.users.get.map(f => T2UserInfo(f))))
      case _ => {
        f.msg match {
          case Some(x) => logger.error(s"Failed to get list user role: $x")
          case _ => logger.error("Failed to get list user role")
        }
        UserInfoPageable(0, None)
      }
    })
  }

  override def searchListUserRole(usernameSearchKey: String, notInRoleIds: Option[Seq[Int]], inRoleIds: Option[Seq[Int]], from: Int, size: Int): Future[UserInfoPageable] = {
    client.searchListUserRole(usernameSearchKey, notInRoleIds, inRoleIds, from, size).map(f => f.code match {
      case 0 => UserInfoPageable(f.total.get, Some(f.users.get.map(f => T2UserInfo(f))))
      case _ => {
        f.msg match {
          case Some(x) => logger.error(s"Failed to get list user role: $x")
          case _ => logger.error("Failed to get list user role")
        }
        UserInfoPageable(0, None)
      }
    })
  }

  override def resetPasswordUser(username: String, newPassword: String): Future[Unit] = {
    client.resetPasswordUser(username, newPassword).map(result => {
      if (result.code != 0) {
        var msg = "Failed to reset password"
        result.msg match {
          case Some(x) => {
            logger.error(x)
            msg = x
          }
          case _ => logger.error("Failed to reset password")
        }
        throw new Exception(msg)
      }
    })
  }

  override def updatePasswordUser(username: String, oldPassword: String, newPassword: String): Future[Unit] = {
    client.updatePasswordUser(username, oldPassword, newPassword).map(result => {
      if (result.code != 0) {
        var msg = "Failed to update password"
        result.msg match {
          case Some(x) => {
            logger.error(x)
            msg = x
          }
          case _ => logger.error("Failed to update password")
        }
        throw new Exception(msg)
      }
    })
  }

  override def isCredentialDefault(oauthType: String, username: String): Future[Boolean] = {
    client.isCredentialDefault(oauthType, username).map(result => result.code match {
      case 0 => result.data.get
      case _ =>
        logger.error(s"Failed check credential [$username]: ${result.msg.getOrElse("None msg")}")
        throw new Exception("Failed when check credential")
    })
  }

  override def getActiveUsername(from: Int, size: Int): Future[Pageable[String]] = {
    client.getActiveUsername(from, size).map(result => result.code match {
      case 0 => Pageable(result.total, result.users)
      case _ =>
        val msg = s"Failed when getActiveUsername($from, $size)"
        logger.error(msg)
        throw new Exception(msg)
    })
  }

  override def loginOAuth(username: String, sessionTimeoutInMS: Long): Future[TUserAuthInfo] = {
    for {
      result <- client.loginOAuth(username, Some(sessionTimeoutInMS))
    } yield {
      result.code match {
        case 0 => result.userAuthInfo.get
        case _ =>
          if (result.msg.isDefined) logger.error(result.msg.get)
          throw InvalidCredentialError("Login info wrong")
      }
    }
  }

  override def hasRole(sessionId: String, roleName: String): Future[Option[Boolean]] = {
    for {
      r <- client.hasRole(sessionId, roleName)
    } yield r.code match {
      case 0 => r.data
      case _ => None
    }
  }

  override def hasRoles(sessionId: String, roleNames: Seq[String]): Future[Option[Boolean]] = {
    for {
      r <- client.hasRoles(sessionId, roleNames)
    } yield r.code match {
      case 0 => r.data.map(!_.exists(_ == false))
      case _ => None
    }
  }

  override def hasRoleUser(username: String, roleName: String): Future[Option[Boolean]] = {
    for {
      r <- client.hasRoleUser(username, roleName)
    } yield r.code match {
      case 0 => r.data
      case _ => None
    }
  }

  override def hasAllRoleUser(username: String, roleNames: Seq[String]): Future[Option[Boolean]] = {
    for {
      r <- client.hasAllRoleUser(username, roleNames)
    } yield r.code match {
      case 0 => r.data
      case _ => None
    }
  }

  override def getAllPermission(username: String): Future[Option[Seq[String]]] = {
    for {
      r <- client.getAllPermission(username)
    } yield r.code match {
      case 0 => r.data
      case _ => None
    }
  }

  override def deleteAllExpiredUserRole(defaultRole: Int, maxTime: Long): Future[Option[Boolean]] = {
    for {
      r <- client.deleteAllExpiredUserRole(defaultRole, maxTime)
    } yield r.code match {
      case 0 => r.data
      case _ => None
    }
  }

  override def getUserRoles(username: String): Future[Option[Seq[TRoleInfo]]] = {
    for {
      r <- client.getAllRoleInfo(username)
    } yield
      r.code match {
        case 0 =>
          val currentTime = System.currentTimeMillis()
          r.roles.map(_.filter(_.expireTime > currentTime))
        case _ => None
      }
  }

  override def insertUserRoles(username: String, roleIds: Set[Int]): Future[Unit] = {
    client.insertUserRoles(username, roleIds).map(result => result.code match {
      case 0 =>
      case _ => {
        var msg = "Failed to insertUserRoles"
        result.msg match {
          case Some(x) => {
            error(x)
            msg = x
          }
          case _ => error("Failed to insertUserRoles")
        }
        throw new Exception(msg)
      }
    })
  }
}
