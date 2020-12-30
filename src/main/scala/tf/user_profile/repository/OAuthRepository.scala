package tf.user_profile.repository

import java.net.SocketTimeoutException

import org.apache.commons.codec.digest.HmacUtils
import scalaj.http.Http
import tf.user_profile.util.ZConfig
import user_caas.domain.thrift.Constants

import scala.util.parsing.json.JSON

/**
 * @author sonpn
 */
trait OAuthRepository {
  def oauthType: String

  def getId: String

  def getUsername: String

  def getName: Option[String]

  def getFamilyName: Option[String]

  def getGivenName: Option[String]

  def getEmail: Option[String]

  def getAvatar: Option[String]

  def getPhoneNumber: Option[String]

  def setPhoneNumber(phoneNumber: String): OAuthRepository
}

object OAuthRepositoryUtils {
  def parseName(name: String, familyName: String, givenName: String): (String, String, String) = {
    var newName: String = name
    var newFamilyName: String = familyName
    var newGivenName: String = givenName
    if (familyName != null || givenName != null) {
      newName = s"${if (familyName != null) familyName else ""} ${if (givenName != null) givenName else ""}"
      if (newName.isEmpty) newName = null
    } else {
      if (name != null) {
        val splitName = name.split(" ")
        newFamilyName = splitName.slice(0, splitName.length).mkString(" ")
        newGivenName = splitName.last
      }
    }
    (newName, newFamilyName, newGivenName)
  }

  def buildUsername(id: String, oauthType: String) = oauthType match {
    case Constants.OAUTH_GOOGLE => s"gg-$id"
    case Constants.OAUTH_FACEBOOK => s"fb-$id"
    case _ => throw new UnsupportedOperationException("Unsupported oauthType=" + oauthType)
  }
}


case class GoogleOAuthRepository(googleId: String, token: String) extends OAuthRepository {

  private val appID = ZConfig.getString("caas.oauth.google.app_id", null)

  val response = try {
    JSON.parseFull(Http("https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=" + token).timeout(5000, 10000).asString.body) match {
      case Some(s: Map[String, String]) => s
      case _ => throw new InternalError("Error when getting google info")
    }
  } catch {
    case e: SocketTimeoutException => throw new SocketTimeoutException("Timeout when getting google info")
    case e: Exception => throw new Exception("Error when getting google info")
  }

  response.get("aud") match {
    case Some(s) if s.equals(appID) =>
    case _ => throw new IllegalArgumentException("Illegal token!")
  }

  val id = response.get("sub") match {
    case Some(s) => s.toString
    case _ => throw new IllegalArgumentException("Illegal token!")
  }

  var familyName: Option[String] = None
  var givenName: Option[String] = None
  var name: Option[String] = None
  parseName()

  var phoneNumber: Option[String] = None

  private def parseName() = {
    val names = OAuthRepositoryUtils.parseName(response.get("name") match {
      case Some(x) => x
      case _ => null
    }, response.get("family_name") match {
      case Some(x) => x
      case _ => null
    }, response.get("given_name") match {
      case Some(x) => x
      case _ => null
    })
    name = Some(names._1)
    familyName = Some(names._2)
    givenName = Some(names._3)
  }

  override def getId: String = id

  override def getUsername: String = OAuthRepositoryUtils.buildUsername(id, oauthType)

  override def getName: Option[String] = name

  override def getFamilyName: Option[String] = familyName

  override def getGivenName: Option[String] = givenName

  override def getEmail: Option[String] = response.get("email")

  override def getAvatar: Option[String] = response.get("picture")

  override def getPhoneNumber: Option[String] = phoneNumber

  override def oauthType: String = Constants.OAUTH_GOOGLE

  override def setPhoneNumber(phoneNumber: String): OAuthRepository = {
    this.phoneNumber = Some(phoneNumber)
    this
  }
}

case class FacebookOAuthRepository(facebookId: String, token: String) extends OAuthRepository {
  private val appSecret = ZConfig.getString("caas.oauth.facebook.app_secret", null)


  val response: Map[String, String] = try {
    val appSecretProof = HmacUtils.hmacSha256Hex(appSecret, token)
    println("https://graph.facebook.com/me/?access_token=$token&appsecret_proof=$appSecretProof&fields=id,name,first_name,last_name,email")
    JSON.parseFull(
      Http(s"https://graph.facebook.com/me/?access_token=$token&appsecret_proof=$appSecretProof&fields=id,name,first_name,last_name,email")
        .timeout(5000, 10000)
        .asString
        .body
    ) match {
      case Some(s: Map[String, String]) => s
      case _ => throw new InternalError("Error when getting facebook info")
    }
  } catch {
    case e: SocketTimeoutException => throw new SocketTimeoutException("Timeout when getting facebook info")
    case e: Exception => throw new Exception("Error when getting facebook info")
  }
  val id = response.get("id") match {
    case Some(s) => s.toString
    case _ => throw new IllegalArgumentException("Illegal token!")
  }

  var name: Option[String] = None
  var familyName: Option[String] = None
  var givenName: Option[String] = None
  parseName()

  var phoneNumber: Option[String] = None

  private def parseName() = {
    val names = OAuthRepositoryUtils.parseName(response.get("name") match {
      case Some(x) => x
      case _ => null
    }, response.get("first_name") match {
      case Some(x) => x
      case _ => null
    }, response.get("last_name") match {
      case Some(x) => x
      case _ => null
    })
    name = Some(names._1)
    familyName = Some(names._2)
    givenName = Some(names._3)
  }

  override def getId: String = id

  override def getEmail: Option[String] = response.get("email")

  override def getUsername: String = OAuthRepositoryUtils.buildUsername(id, oauthType)

  override def getName: Option[String] = name

  override def getFamilyName: Option[String] = familyName

  override def getGivenName: Option[String] = givenName

  override def getAvatar: Option[String] = Some("https://graph.facebook.com/" + id + "/picture?type=large")

  override def getPhoneNumber: Option[String] = phoneNumber

  override def oauthType: String = Constants.OAUTH_FACEBOOK

  override def setPhoneNumber(phoneNumber: String): OAuthRepository = {
    this.phoneNumber = Some(phoneNumber)
    this
  }
}