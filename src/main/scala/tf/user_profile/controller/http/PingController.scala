package tf.user_profile.controller.http

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import tf.user_profile.controller.http.filter.common.TestFilter

import scala.util.parsing.json.JSONObject

/**
 * @author anhlt
 */
class PingController extends Controller {

  filter[TestFilter]
    .get("/user/ping") {
      request: Request => {
        response.ok(JSONObject(Map("status"->"ok","data"->"pong")).toString())
      }
    }
}
