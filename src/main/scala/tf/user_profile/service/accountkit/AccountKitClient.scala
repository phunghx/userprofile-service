package tf.user_profile.service.accountkit

import java.net.{HttpCookie, Proxy, URI}

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.twitter.inject.Logging
import org.apache.commons.codec.digest.HmacUtils
import org.apache.http.client.utils.URLEncodedUtils
import scalaj.http.{Http, HttpOptions, HttpResponse}

import scala.collection.JavaConversions._
import scala.collection.mutable

/**
 * @author anhlt
 */
object AccountKitClient {
  val BASE_URL = "https://www.accountkit.com"
  val INIT_SMS_LOGIN = "/v1.0/basic/dialog/sms_login/"
  val SEND_SMS_LOGIN = "/v1.0/basic/dialog/sms_login/sendsms/"
  val CONFIRM_SMS_CODE = "/v1.0/basic/dialog/sms_login/confirm/"

  val GET_ACCESS_TOKEN_URL = "https://graph.accountkit.com/v1.3/access_token"
  val RETRIEVE_ACCESS_TOKEN_URL = "https://graph.accountkit.com/v1.3/access_token"

  implicit class HttpResponseImplicit(response: HttpResponse[String]) {
    def asDocument(url: String) = {
      HtmlResolver(response.body).resolve(url).document
    }

    def debug: HttpResponse[String] = {
      println(
        s"""
           |Code: ${response.code}
           |Header:
           |        ${response.headers.map(f => s"${f._1}=${f._2.mkString("\t")}").mkString("\n        ")}
           |Body:
           |${response.body}
       """.stripMargin)

      response
    }
  }

  def fromHttpCookie(c: HttpCookie): CookiePersistent = {
    CookiePersistent(
      name = c.getName,
      value = c.getValue,
      domain = c.getDomain,
      isSecure = c.getSecure,
      path = c.getPath,
      maxAge = c.getMaxAge
    )
  }

  val phoneUtil = PhoneNumberUtil.getInstance()

  def normalizePhoneE164(phone: String, regionDefault: String) = {
    val pn = phoneUtil.parse(phone, regionDefault)
    (s"+${pn.getCountryCode}", pn.getNationalNumber.toString)
  }

  def splitQueryFromUrl(url: String): Map[String, String] = {
    URLEncodedUtils.parse(new URI(url), "UTF-8").toSeq.map(f => f.getName -> f.getValue).toMap
  }
}

trait AccountKitPersistentStorage {

  def set(countryCode: String, phoneNumber: String, data: ConfirmData): Unit

  def get(countryCode: String, phoneNumber: String): Option[ConfirmData]

  def remove(countryCode: String, phoneNumber: String): Unit
}

case class ConfirmData(url: String, form: Map[String, String], cookies: Seq[CookiePersistent], confirmCode: Option[String] = None)

case class CookiePersistent(name: String, value: String, domain: String, isSecure: Boolean, path: String, maxAge: Long) {
  def httpCookie: HttpCookie = {
    val cookie = new HttpCookie(name, value)
    cookie.setDomain(domain)
    cookie.setSecure(isSecure)
    cookie.setPath(path)
    cookie.setMaxAge(maxAge)
    cookie
  }
}

import AccountKitClient._

case class AccountKitClient(appId: String,
                            appSecret: String,
                            redirectUrl: String,
                            regionDefault: String = "VN",
                            storage: AccountKitPersistentStorage) extends Logging {

  val browser = ScalajHttpClient()

  val appAccessToken = s"AA|$appId|$appSecret"
  val appSecretProof = HmacUtils.hmacSha256Hex(appSecret, appAccessToken)

  val defaultHeaders = Map(
    "user-agent" -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.94 Safari/537.36",
    "upgrade-insecure-requests" -> "1"
  )

  def send(phone: String): Option[String] = {
    var lastHtml: (Int, String) = (-1, "")
    try {
      val (countryCode, phoneNumber) = normalizePhoneE164(phone, regionDefault)

      browser.clearCookies()
      var httpResp = browser.post(url = s"$BASE_URL$INIT_SMS_LOGIN", postParams = Map(
        "app_id" -> appId,
        "country_code" -> countryCode, "phone_number" -> phoneNumber, "state" -> phone,
        "redirect" -> redirectUrl,
        "fbAppEventsEnabled" -> "true"
      ))
      //          .debug
      lastHtml = (httpResp.code, httpResp.body)
      var document = httpResp.asDocument(BASE_URL)

      val sendSmsUrl = document.select(s"form[action*=$SEND_SMS_LOGIN]").get(0).attr("action")
      val sendSmsForm = HtmlResolver.formParams(s"form[action*=$SEND_SMS_LOGIN]", document)(0)
      //      println(sendSmsUrl)
      //      println(sendSmsForm)

      httpResp = browser.post(url = sendSmsUrl, postParams = sendSmsForm ++ Map("next_view" -> "sms_login_confirmation_view"))
      //        .debug
      lastHtml = (httpResp.code, httpResp.body)
      document = httpResp.asDocument(BASE_URL)
      val confirmSmsUrl = document.select(s"form[action*=$CONFIRM_SMS_CODE]").get(0).attr("action")
      val confirmSmsForm = HtmlResolver.formParams(s"form[action*=$CONFIRM_SMS_CODE]", document)(0)
      //      println(confirmSmsUrl)
      //      println(confirmSmsForm)

      val confirmData = ConfirmData(url = confirmSmsUrl, form = confirmSmsForm, cookies = browser.getCookies.map(fromHttpCookie))
      storage.set(countryCode, phoneNumber, confirmData)
      None
    } catch {
      case e: Exception =>
        error(s"Failed when send($phone).Last html:\n{$lastHtml}", e)
        Some(e.getMessage)
    }
  }

  def verify(phone: String, confirmCode: String, delete: Boolean): Option[String] = {
    var lastHtml: (Int, String) = (-1, "")
    try {
      val (countryCode, phoneNumber) = normalizePhoneE164(phone, regionDefault)
      val confirmData = storage.get(countryCode, phoneNumber).getOrElse(throw new Exception("invalid_confirm_code"))

      if (confirmData.confirmCode.filter(_.nonEmpty).contains(confirmCode)) {
        if (delete) storage.remove(countryCode, phoneNumber)
        return None
      }

      val form = confirmData.form ++ Map("confirmation_code" -> confirmCode)

      Option(confirmData.cookies).getOrElse(Nil).foreach(f => browser.setCookie(f.name, f.httpCookie))

      val httpResp = browser.post(url = confirmData.url, postParams = form, headers = defaultHeaders)
      //        .debug
      lastHtml = (httpResp.code, httpResp.body)
      //      val document = httpResp.asDocument(BASE_URL)

      var errorMsg: Option[String] = Some("unknown_error")
      httpResp.code match {
        case 302 =>
          val optLocation = httpResp.headers.find(p => p._1.toLowerCase == "location").flatMap(_._2.headOption)
          optLocation match {
            case Some(location) =>
              if (splitQueryFromUrl(location).getOrElse("status", "") == "PARTIALLY_AUTHENTICATED") {
                if (delete) storage.remove(countryCode, phoneNumber)
                else {
                  storage.set(countryCode, phoneNumber, confirmData.copy(confirmCode = Some(confirmCode)))
                }
                errorMsg = None
              }
            case _ =>
          }
        case 200 => errorMsg = Some("invalid_confirm_code")
        case _ =>
      }
      errorMsg
    } catch {
      case e: Exception =>
        error(s"Failed when confirm($phone, $confirmCode).Last html:\n{$lastHtml}", e)
        Some(e.getMessage)
    }
  }

  def removeVerifyCode(phone: String): Unit = {
    val (countryCode, phoneNumber) = normalizePhoneE164(phone, regionDefault)
    storage.remove(countryCode, phoneNumber)
  }
}

sealed case class ScalajHttpClient(connectTimeout: Int = 30000, readTimeout: Int = 60000) {

  val cookies = new mutable.HashMap[String, HttpCookie]()

  def get(url: String,
          headers: Map[String, String] = null,
          getParams: Map[String, String] = null,
          postParams: Any = null,
          proxy: Option[Proxy] = None) = {
    val response = makeRequest("GET", url, headers, getParams, postParams, proxy).asString

    response.cookies.foreach(c => {
      cookies.put(c.getName, c)
    })
    response
  }

  def post(url: String,
           headers: Map[String, String] = null,
           getParams: Map[String, String] = null,
           postParams: Map[String, String] = null,
           proxy: Option[Proxy] = None) = {
    val response = makeRequest("POST", url, headers, getParams, postParams, proxy).asString
    response.cookies.foreach(c => {
      cookies.put(c.getName, c)
    })
    response
  }


  private def makeRequest(method: String,
                          url: String,
                          headers: Map[String, String] = null,
                          getParams: Map[String, String] = null,
                          postData: Any = null,
                          proxy: Option[Proxy] = None) = {

    var builder = Http(url)
      .option(HttpOptions.allowUnsafeSSL)
      //            .option(HttpOptions.followRedirects(true))
      .timeout(connectTimeout, readTimeout)


    if (headers != null && headers.nonEmpty)
      builder = builder.copy(headers = headers.toSeq)

    if (getParams != null)
      builder = builder.params(getParams)


    postData match {
      case null if method.equalsIgnoreCase("put") => builder = builder.put("")
      case x: Map[_, _] if x != null && x.nonEmpty =>
        val data = x.asInstanceOf[Map[String, String]]
        if (method.equalsIgnoreCase("put"))
          builder = builder.put("")
        else
          builder = builder.postForm(data.toSeq)
      case x: Seq[_] if x != null && x.nonEmpty =>
        val data = x.asInstanceOf[Seq[(String, String)]]
        if (method.equalsIgnoreCase("put"))
          builder = builder.put("")
        else
          builder = builder.postForm(data)
      case data: String if data != null && data.nonEmpty =>
        if (method.equalsIgnoreCase("put"))
          builder = builder.put("")
        else
          builder = builder.postData(data)
      case _ if method.equalsIgnoreCase("put") => builder = builder.put("")
      case _ =>
    }
    proxy match {
      case Some(p) => builder = builder.proxy(p)
      case None =>
    }

    builder = builder.cookies(cookies.values.toIndexedSeq)
    builder.header("Accept", "*/*")
  }

  def hasCookie(cookieName: String) = {
    cookies.contains(cookieName)
  }

  def getCookie(cookieName: String) = {
    cookies.get(cookieName).fold[String]({
      null
    })(c => c.getValue)
  }

  def setCookie(name: String, c: HttpCookie): Unit = {
    cookies.put(name, c)
  }

  def getCookies: Seq[HttpCookie] = cookies.values.toSeq

  def clearCookies(): Unit = cookies.clear()
}
