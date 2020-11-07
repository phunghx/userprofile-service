package datainsider.user_profile.service.verification

import com.twitter.util.Future

/**
  * @author anhlt
  */
trait VerifyService {

  def isExceedQuota(receiver: String): Future[Boolean]

  def incrQuota(receiver: String): Future[Unit]

  def genAndSendVerifyCode(receiver: String): Future[Boolean]

  def genAndSendForgotPasswordCode(receiver: String): Future[Boolean]

  def verifyCode(receiver: String, code: String, delete: Boolean): Future[Unit]

  def deleteVerifyCode(receiver: String): Future[Unit]

  def genTokenWithEmail(receiver: String): Future[String]

  def verifyEmailToken(token: String, delete: Boolean): Future[String]

  def deleteEmailToken(token: String): Future[Unit]


}