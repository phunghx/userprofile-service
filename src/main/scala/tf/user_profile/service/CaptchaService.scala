package tf.user_profile.service

import com.fasterxml.jackson.databind.JsonNode
import com.twitter.inject.Logging
import com.twitter.util.Future
import tf.user_profile.domain.Implicits._
import datainsider.user_profile.util.ZConfig
import scalaj.http.Http
import tf.user_profile.util.{JsonParser, ZConfig}

/**
 * @author anhlt
 */
trait CaptchaService {
  def verifyCaptcha(token: String): Future[Boolean]
}

class ReCaptchaServiceImpl extends CaptchaService with Logging {
  val url = ZConfig.getString("recaptcha.url_verify")
  val secretKey = ZConfig.getString("recaptcha.secret_key")

  override def verifyCaptcha(token: String): Future[Boolean] = futurePool {
    try {
      val req = Http(url)
        .param("secret", secretKey)
        .param("response", token)
      val resp = req.asString
      resp.code match {
        case 200 => JsonParser.fromJson[JsonNode](resp.body).path("success").asBoolean
        case _ => false
      }
    } catch {
      case e: Exception => error(e.toString, e)
        false
    }
  }
}
