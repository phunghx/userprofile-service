package tf.user_profile.module

import com.google.common.net.MediaType._
import com.google.inject.Inject
import com.twitter.finatra.http.marshalling
import com.twitter.finatra.http.marshalling.{DefaultMessageBodyWriter, WriterResponse}
import com.twitter.finatra.jackson.ScalaObjectMapper
import com.twitter.inject.Logging

case class ApiError(code: Int,
                    reason: String,
                    message: String,
                    data: Option[Any] = None)

case class BaseResponse(success: Boolean,
                        data: Option[Any],
                        error: Option[ApiError])

class CustomResponseWriterImpl @Inject()(mapper: ScalaObjectMapper) extends DefaultMessageBodyWriter with Logging {

  override def write(obj: Any): WriterResponse = {
    obj match {
      case ex: Throwable =>
        marshalling.WriterResponse(
          contentType = JSON_UTF_8.toString,
          body = mapper.writeValueAsString(ApiError(500, "internal_error", ex.getMessage))
        )

      case a: Any =>
        marshalling.WriterResponse(
          contentType = JSON_UTF_8.toString,
          body = mapper.writeValueAsString(a)
        )

    }

  }
}
