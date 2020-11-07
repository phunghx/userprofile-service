package datainsider.user_profile.service.verification

import com.twitter.inject.Logging
import com.twitter.util.Future
import datainsider.user_profile.domain.Implicits._
import org.apache.commons.mail._

/**
  * @author anhlt
  */

trait ChannelService {
  def sendMessage(receiver: String, subject: String, message: String): Future[Unit]
}

case class EmailChannelService(host: String,
                               port : Int,
                               username: String,
                               password: String) extends ChannelService with Logging {

  override def sendMessage(email: String, subject: String, message: String): Future[Unit] = {
    val commonsMail: Email = new SimpleEmail().setMsg(message)
    commonsMail.setAuthentication(username, password)
    commonsMail.setHostName(host)
    commonsMail.setSmtpPort(port)
    commonsMail.setSSL(true)
    commonsMail.addTo(email)
    commonsMail.setFrom(email, "XED").setSubject(subject)
      .send()
      .onSuccess(_ => {})
      .onFailure(fn => throw new Exception("Send email faild"))
      .map(f => {
        //DO Some thing
      })
  }

}


