package tf.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.util.JsonParser
import tf.user_profile.controller.http.filter.email.EmailFilterRequest
import tf.user_profile.domain.request.UpdateEmailBodyRequest
import tf.user_profile.util.JsonParser

/**
 * @author anhlt
 */

case class UpdateEmailRequest(email: String) extends EmailFilterRequest {
  override def getEmail(): String = email
}

class UpdateEmailParser extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val bodyRequest = JsonParser.fromJson[UpdateEmailBodyRequest](request.contentString)
    DataRequestContext.setDataRequest(request, UpdateEmailRequest(bodyRequest.email))
    service(request)
  }
}
