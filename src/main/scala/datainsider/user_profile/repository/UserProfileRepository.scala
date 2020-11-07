package datainsider.user_profile.repository

import datainsider.user_profile.domain.profile.UserProfile
import datainsider.user_profile.util.JsonParser
import org.nutz.ssdb4j.spi.SSDB

import scala.collection.JavaConversions._


trait UserProfileRepository {

  def mapEmailWithUserId(email: String, userId: String): Boolean

  def getUserIdByEmail(email: String): Option[String]

  def isExistEmail(email: String):Boolean

  def deleteEmail(email: String): Boolean


  def mapPhoneWithUserId(phoneNumber: String, userId: String): Boolean

  def getUserIdByPhone(phoneNumber: String): Option[String]

  def isExistPhone(phoneNumber: String): Boolean

  def deletePhone(phoneNumber: String): Boolean


  def addProfile(profile: UserProfile) : Boolean

  def deleteProfile(userId: String) : Boolean

  def getProfile(userId: String): Option[UserProfile]

  def getProfileByEmail(email: String): Option[UserProfile]

  def getProfiles(ids: Seq[String]): Map[String, UserProfile]

  def getAllUserProfiles(): Map[String,UserProfile]
}

case class UserProfileRepositoryImpl(ssdb: SSDB,
                                     usernameKey: String,
                                     emailKey: String,
                                     phoneNumberKey: String) extends UserProfileRepository {

  override def addProfile(userProfile: UserProfile): Boolean = {
    val r = ssdb.hset(usernameKey, userProfile.username, serializeProfile(userProfile))
    if (r.ok()) {
      userProfile.email.foreach(email => {
        mapEmailWithUserId(email, userProfile.username)
      })
      userProfile.phone.foreach(phone => {
        mapPhoneWithUserId(phone, userProfile.username)
      })
      true
    } else {
      false
    }
  }


  override def deleteProfile(userId: String) =  {
    val resp = ssdb.hdel(usernameKey, userId)
    resp.ok
  }

  override def getProfileByEmail(email: String) =  {
    getUserIdByEmail(email).flatMap(getProfile(_))
  }

  override def getProfile(userId: String) =  {
    val r = ssdb.hget(usernameKey, userId)
    if (r.ok()) {
      val profile = deserializeProfile(r.asString())
      if (profile.username == null) profile.username = userId
      Some(profile)
    } else None
  }

  override def getProfiles(userIds: Seq[String]): Map[String, UserProfile] = {
    val resp = ssdb.multi_hget(usernameKey, userIds: _*)
    if (resp.ok()) {
      resp.mapString()
        .map(e => e._1 -> deserializeProfile(e._2))
        .toMap
    } else Map.empty
  }

  override def mapPhoneWithUserId(phoneNumber: String, userId: String): Boolean =  {
    val resp = ssdb.hset(phoneNumberKey, phoneNumber, userId)
    resp.ok
  }

  override def getUserIdByPhone(phoneNumber: String): Option[String] = {
    val resp = ssdb.hget(phoneNumberKey, phoneNumber)
    if (resp.ok()) {
      Some(resp.asString())
    } else {
      None
    }
  }

  override def isExistPhone(phoneNumber: String): Boolean = getUserIdByPhone(phoneNumber).isDefined

  override def deletePhone(phoneNumber: String): Boolean = {
    val resp = ssdb.hdel(phoneNumberKey, phoneNumber)
    resp.ok
  }


  override def mapEmailWithUserId(email: String, userId: String): Boolean =  {
    val resp = ssdb.hset(emailKey, email, userId)
    resp.ok
  }

  override def getUserIdByEmail(email: String): Option[String] = {
    val resp = ssdb.hget(emailKey,email )
    if (resp.ok()) {
      Some(resp.asString())
    } else {
      None
    }
  }

  override def isExistEmail(email: String): Boolean =  getUserIdByEmail(email).isDefined

  override def deleteEmail(email: String): Boolean = {
    val resp = ssdb.hdel(emailKey, email)
    resp.ok
  }


  override def getAllUserProfiles() = {
    val resp = ssdb.hgetall(usernameKey)
    if (resp.ok()) {
      resp.mapString()
        .map(e => e._1 -> deserializeProfile(e._2))
        .toMap
    } else Map.empty
  }

  private def serializeProfile(profile: UserProfile): String = {
    JsonParser.toJson[UserProfile](profile)
  }

  private def deserializeProfile(json: String) = {
    JsonParser.fromJson[UserProfile](json)
  }

}
