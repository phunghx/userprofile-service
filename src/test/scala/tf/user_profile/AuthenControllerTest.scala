package tf.user_profile

import com.twitter.finagle.thrift.ThriftClient
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import tf.user_profile.domain.response.UserResponse


/**
  * @author anhlt
  */
class AuthenOAuthenTest extends FeatureTest {
  override protected val server = new EmbeddedHttpServer(twitterServer = new Server) with ThriftClient
  val users = scala.collection.mutable.Map[String, String]()


}
