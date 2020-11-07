package datainsider.user_profile.controller.http.filter.parser

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.email.EmailFilterRequest
import datainsider.user_profile.domain.request.UpdateEmailBodyRequest
import datainsider.user_profile.util.{ JsonParser}

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
