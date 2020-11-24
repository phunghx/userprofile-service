package tf.user_profile

import com.google.inject.Module
import com.google.inject.util.Modules
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{CommonFilters, ExceptionMappingFilter}
import com.twitter.finatra.http.routing.HttpRouter
import com.twitter.finatra.thrift.ThriftServer
import com.twitter.finatra.thrift.routing.ThriftRouter
import tf.user_profile.controller.http.filter.common.CORSFilter
import tf.user_profile.controller.http.UserController
import tf.user_profile.module.UserProfileModule
import tf.user_profile.controller.http.filter.common.{CORSFilter, CaseClassExceptionMapping, CommonExceptionMapping, JsonParseExceptionMapping}
import tf.user_profile.controller.http.{AuthController, PingController, UserController}
import tf.user_profile.controller.http.filter.user.AuthUserParser
import tf.user_profile.controller.thrift.UserProfileController
import tf.user_profile.module.{UserProfileModule, UserProfileModuleTestImpl}
import tf.user_profile.util.ZConfig

/**
 * Created by SangDang on 9/8/
 **/
object MainApp extends Server

class TestServer extends Server {

  override def modules: Seq[com.google.inject.Module] = Seq(overrideModule(super.modules ++ Seq(UserProfileModuleTestImpl): _*))

  private def overrideModule(modules: Module*): Module = {
    if (modules.size == 1) return modules.head

    var module = modules.head
    modules.tail.foreach(m => {
      module = Modules.`override`(module).`with`(m)
    })
    module
  }
}

class Server extends HttpServer with ThriftServer {

  override protected def defaultHttpPort: String = ZConfig.getString("server.http.port", ":8080")

  override protected def defaultThriftPort: String = ZConfig.getString("server.thrift.port", ":8082")

  override protected def disableAdminHttpServer: Boolean = ZConfig.getBoolean("server.admin.disable", true)

  override def modules: Seq[Module] = Seq(UserProfileModule)

  override def messageBodyModule = com.twitter.finatra.MessageBodyModule


  override protected def configureHttp(router: HttpRouter): Unit = {
    router
      .filter[CORSFilter](beforeRouting = true)
      .filter[CommonFilters]
      .filter[AuthUserParser]
      .filter[ExceptionMappingFilter[Request]]

      .add[AuthController]
      .add[UserController]
      .add[PingController]
      .exceptionMapper[CaseClassExceptionMapping]
      .exceptionMapper[JsonParseExceptionMapping]
      .exceptionMapper[CommonExceptionMapping]
  }

  override protected def configureThrift(router: ThriftRouter): Unit = {
    router
      .filter[com.twitter.finatra.thrift.filters.AccessLoggingFilter]
      .add[UserProfileController]
  }

  override def afterPostWarmup(): Unit = {
    super.afterPostWarmup()
    info("afterPostWarmup")
  }
}
