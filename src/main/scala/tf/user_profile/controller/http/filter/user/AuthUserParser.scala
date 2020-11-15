package tf.user_profile.controller.http.filter.user

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.domain.ThriftImplicit._
import javax.inject.Inject
import tf.user_profile.domain.profile.UserInfo
import tf.user_profile.service.CaasService
import tf.user_profile.util.ZConfig

/**
 * @author anhlt
 */

case class UserAuthInfo(sessionId: Option[String],
                        userInfo: Option[UserInfo],
                        username: Option[String])

class AuthUserParser @Inject()(caasService: CaasService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    UserContext.setUser(request, caasService)
      .flatMap(_ => service(request))
  }
}

object UserContext {
  private val UserAuthField = Request.Schema.newField[UserAuthInfo]()
  val sessionKey = ZConfig.getString("session.name")
  val authorizationKey = ZConfig.getString("session.authorization")

  def setUser(request: Request, caasService: CaasService): Future[Unit] = {
    getSession(request) match {
      case Some(x) => getUserInfo(x, caasService).map(user => request.ctx.update(UserAuthField, UserAuthInfo(Some(x), user,user.map(_.username) )))
      case _ => Future.value(request.ctx.update(UserAuthField, UserAuthInfo(None,None, None)))
    }
  }

  private def getUserInfo(sessionId: String, caasService: CaasService): Future[Option[UserInfo]] = {
    caasService.getUserWithSessionId(sessionId).map({
      case Some(x) => Option(x)
      case _ => None
    })
  }

  private def getSession(request: Request): Option[String] = {
    val authenCookie = request.cookies.get(sessionKey).map(_.value)
    val authenHeader = request.headerMap.get(authorizationKey)

    if(authenCookie.isDefined) authenCookie else authenHeader
  }


  implicit class UserContextSyntax(val request: Request) extends AnyVal {
    def user: UserAuthInfo = request.ctx(UserAuthField)
  }

}
