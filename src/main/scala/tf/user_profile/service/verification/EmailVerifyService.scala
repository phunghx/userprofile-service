package tf.user_profile.service.verification

import java.util.UUID

import com.twitter.inject.Logging
import com.twitter.util.{Future, Return, Throw}
import tf.user_profile.domain.Implicits._
import tf.user_profile.exception.QuotaExceedError
import tf.user_profile.repository.SSDBKeyValueRepository.KeyValueRepositoryAsync
import tf.user_profile.util.ZConfig
import tf.user_profile.domain.profile.VerifyCodeInfo
import tf.user_profile.exception.{InternalError, QuotaExceedError, VerificationCodeInvalidError}
import tf.user_profile.repository.KeyValueRepository
import tf.user_profile.util.{JsonParser, LoggerUtils, Utils, ZConfig}

/**
  * @author anhlt
  */
case class VerificationConfig(verificationCodeTemplate: String,
                              forgotPasswordCodeTemplate: String,
                              defaultTestCode: String,
                              expireTimeInSecond: Int,
                              quota: Int,
                              quotaCountdown: Int)

case class EmailVerifyService(tokenRepository: KeyValueRepository[String, String],
                              quotaRepository: KeyValueRepository[String, Int],
                              emailChannel: ChannelService,
                              verificationConfig: VerificationConfig) extends VerifyService with Logging {

  private val trackLogger = LoggerUtils.getLogger("SendCodeViaEmailLogger")

  private def buildQuotaKey(email: String): String = s"quota-$email"

  private def buildVerifyCodeKey(email: String): String = s"verify-$email"

  private def buildTokenKey(token: String): String = s"token-email-$token"

  private def buildMessageKey(id: String): String = s"email-$id"

  override def isExceedQuota(email: String): Future[Boolean] = {
    val key = buildQuotaKey(email)
    for {
      r <- quotaRepository.asyncGet(key)
      count = r.getOrElse(0)
    } yield count > verificationConfig.quota
  }

  override def incrQuota(email: String): Future[Unit] = {
    val quotaKey = buildQuotaKey(email)
    for {
      count <- quotaRepository.asyncIncr(quotaKey, 1).transform({
        case Return(r) => Future.value(r)
        case Throw(e) =>
          logger.error(s"Error to incr quota for $email", e)
          Future.exception(e)
      })
    } yield count >= verificationConfig.quota match {
      case true =>
        quotaRepository.expire(quotaKey, verificationConfig.quotaCountdown)
      case _ =>
    }
  }

  override def genAndSendVerifyCode(email: String): Future[Boolean] = {
    for {
      code <- genCode(email)
      msg = buildVerificationCodeEmail(email, code)
      r <- sendVerifyCodeToEmail(email, code,"XED Verification Code", msg).transform({
        case Return(r) => Future.True
        case Throw(e) =>
          logger.error(s"Can't send code $code to $email", e)
          Future.False
      })
      _ <- if(r) incrQuota(email) else Future.Unit
    } yield r match {
      case true => r
      case _ => throw InternalError(Some(s"Can't send email to $email."))
    }
  }

  override def genAndSendForgotPasswordCode(email: String): Future[Boolean] = {
    for {
      code <- genCode(email)
      msg = buildForgotPasswordCodeEmail(email, code)
      r <- sendVerifyCodeToEmail(email, code,"XED Forgot Password Code", msg).transform({
        case Return(r) => Future.True
        case Throw(e) =>
          logger.error(s"Can't send code $code to $email", e)
          Future.False
      })
      _ <- if(r) incrQuota(email) else Future.Unit
    } yield r match {
      case true => r
      case _ => throw InternalError(Some(s"Can't send email to $email."))
    }
  }

  private def genCode(email: String): Future[String] = {
    val codeKey = buildVerifyCodeKey(email)
    for {
      isExceedQuota <- isExceedQuota(email)
      code = isExceedQuota match {
        case true => {
          val quotaKey = buildQuotaKey(email)
          val timeleft = quotaRepository.timeLeft(quotaKey)
          throw QuotaExceedError("Please try after : " + timeleft.get + " seconds")
        }
        case _ =>
          val code = if(ZConfig.isDevelopmentMode)
            verificationConfig.defaultTestCode
          else
            Utils.randomInt(100000, 999999).toString
          tokenRepository.put(codeKey, code, Some(verificationConfig.expireTimeInSecond))
          code
      }
    } yield {
      code
    }
  }


  private def sendVerifyCodeToEmail(email: String, code: String , subject: String, msg : String): Future[Unit] = {
    val uuid = UUID.randomUUID().toString

    val codeInfo = VerifyCodeInfo(email, code, System.currentTimeMillis())
    val data: String = JsonParser.toJson[VerifyCodeInfo](codeInfo)

    trackLogger.info(s"Send email: \t$uuid\n$data")

    emailChannel.sendMessage(email, subject, msg)
      .onSuccess(_ => trackLogger.info(s"1\t$uuid"))
      .onFailure(fn => trackLogger.info(s"-1\t$uuid\t${fn.getMessage}"))
      .map(f => {
        tokenRepository.asyncPut(buildMessageKey(uuid), data)
          .onFailure(fn => logger.error(s"Failed to save verify code info: [$data]", fn))
      })
  }


  private def buildVerificationCodeEmail(email: String, code: String): String = {
    verificationConfig.verificationCodeTemplate.replaceFirst("\\$code", code)
  }

  private def buildForgotPasswordCodeEmail(email: String, code: String): String = {
    verificationConfig.forgotPasswordCodeTemplate.replaceFirst("\\$code", code)
  }



  override def verifyCode(email: String, requestedCode: String, delete: Boolean): Future[Unit] = {
    def throwIfNotExist[T](v: Option[T]) = v match {
      case Some(v) => v
      case _ => throw VerificationCodeInvalidError(s"No code was found.")
    }

    val key = buildVerifyCodeKey(email)
    for {
      code <- tokenRepository.asyncGet(key).map(throwIfNotExist)
    } yield code.equals(requestedCode) match {
      case true => if (delete) tokenRepository.delete(key)
      case _ =>
        error(s"${getClass.getSimpleName}: your requested code ${requestedCode} != $code")
        throw VerificationCodeInvalidError(s"The code is invalid.")
    }
  }

  override def deleteVerifyCode(phoneNum: String): Future[Unit] = {
    val key = buildVerifyCodeKey(phoneNum)
    tokenRepository.asyncDelete(key).onFailure(ex => logger.error("Failed to delete verify phone code", ex))
  }

  override def genTokenWithEmail(email: String): Future[String] = {
    val token = UUID.randomUUID().toString
    val key = buildTokenKey(token)
    for {
      _ <- tokenRepository.asyncPut(key, email)
      _ <- tokenRepository.asyncExpire(key, 3 * verificationConfig.expireTimeInSecond).onFailure(ex => {
        tokenRepository.asyncDelete(key)
        throw ex
      })
    } yield token
  }

  override def verifyEmailToken(token: String, delete: Boolean): Future[String] = {
    val key = buildTokenKey(token)
    tokenRepository.asyncGet(key).map({
      case Some(x) =>
        if (delete) tokenRepository.asyncDelete(key).onFailure(ex => logger.error("Failed to delete token phone info", ex))
        x
      case None => throw InternalError(Some("Invalid token."))
    })
  }

  override def deleteEmailToken(token: String): Future[Unit] = {
    val key = buildTokenKey(token)
    tokenRepository.asyncDelete(key).onFailure(ex => logger.error("Failed to delete token phone info", ex))
  }


}
