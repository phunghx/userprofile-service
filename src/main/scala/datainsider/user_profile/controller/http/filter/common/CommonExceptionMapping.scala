package datainsider.user_profile.controller.http.filter.common

import com.fasterxml.jackson.core.JsonParseException
import com.google.inject.Singleton
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.finatra.jackson.caseclass.exceptions.CaseClassMappingException
import com.twitter.inject.Logging
import datainsider.user_profile.exception.XedException
import datainsider.user_profile.module.{ApiError}
import javax.inject.Inject

/**
 * @author anhlt
 */

@Singleton
class CommonExceptionMapping @Inject()(response: ResponseBuilder) extends ExceptionMapper[Throwable] with Logging {

  override def toResponse(request: Request, ex: Throwable): Response = {
    logError(ex)
    val apiError = ex match {
      case ex: XedException =>
        ApiError(ex.getStatus.code,ex.reason, ex.getMessage)
      case _ =>
        ApiError(Status.InternalServerError.code,"internal_error", ex.getMessage,   None)
    }
    response.status(apiError.code).json(apiError)
  }

  private def logError(ex: Throwable): Unit = {
    logger.error(s"${ex.getClass.getName}: ${ex.getMessage}")
  }
}

@Singleton
class CaseClassExceptionMapping @Inject()(response: ResponseBuilder) extends ExceptionMapper[CaseClassMappingException] with Logging {
  override def toResponse(request: Request, throwable: CaseClassMappingException): Response = {
    error("", throwable)
    response.badRequest.json(ApiError(
      Status.BadRequest.code,
      reason = "invalid_param",
      throwable.errors.head.getMessage)
    )
  }
}

@Singleton
class JsonParseExceptionMapping @Inject()(response: ResponseBuilder) extends ExceptionMapper[JsonParseException] with Logging {
  override def toResponse(request: Request, throwable: JsonParseException): Response = {
    response.badRequest.json(ApiError(
      Status.BadRequest.code,
      reason = "invalid_json_format",
      throwable.getMessage)
    )
  }
}

