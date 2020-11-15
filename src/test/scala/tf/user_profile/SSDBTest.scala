package tf.user_profile

import com.google.inject.Guice
import com.twitter.inject.{Injector, IntegrationTest}
import org.nutz.ssdb4j.SSDBs
import org.nutz.ssdb4j.spi.SSDB
import tf.user_profile.module.UserProfileModule
import tf.user_profile.util.ZConfig


/**
  * @author anhlt
  */
class SSDBTest extends IntegrationTest {
  override protected def injector: Injector = Injector(Guice.createInjector(Seq(UserProfileModule):_*))

  private val emailKey = ZConfig.getString("db.ssdb.user_profile.email_key")

  var client: SSDB = SSDBs.pool(
    "34.87.143.227",
//  ZConfig.getString("ssdb.user_profile.host"),
    ZConfig.getInt("db.ssdb.user_profile.port"),
    ZConfig.getInt("db.ssdb.user_profile.timeout_in_ms"),
    null)


  test( "Map email with user id") {
    val username = "up-47e4699e-e124-491c-bd10-6bb3956fd168"
    val email = "anbeel191@gmail.com"
  }

}