package datainsider.user_profile.service

import java.io.{BufferedWriter, File, FileOutputStream, OutputStreamWriter}
import java.util.concurrent.atomic.AtomicInteger

import com.twitter.inject.Logging
import com.twitter.util.Future
import datainsider.user_profile.controller.http.filter.parser.{EditUserProfileRequest, EditUserSettingRequest}
import datainsider.user_profile.domain.Implicits._
import datainsider.user_profile.domain.Pageable
import datainsider.user_profile.domain.profile.{ UserProfile}
import datainsider.user_profile.exception.InternalError
import datainsider.user_profile.repository.UserProfileRepository
import datainsider.user_profile.util.{JsonParser, Utils, ZConfig}
import org.nutz.ssdb4j.spi.SSDB

import scala.collection.JavaConversions._
/**
  * @author anhlt.
  */

trait UserProfileService {
  def editUserSettings(request: EditUserSettingRequest): Future[Boolean]

  def addUserProfile(id: String, info: UserProfile): Future[Boolean]

  def editUserProfile(request: EditUserProfileRequest): Future[UserProfile]

  def deleteUserProfile(id: String): Future[Boolean]

  def getUserProfileByEmail(email: String): Future[Option[UserProfile]]

  def getUserProfile(id: String): Future[Option[UserProfile]]

  def getUserProfiles(ids: Seq[String]): Future[Map[String, UserProfile]]

  def getActiveUserProfiles(from: Int, size: Int): Future[Pageable[UserProfile]]

  def getAllUserProfiles(): Future[Map[String, UserProfile]]

  def getOrAddUserProfile(userId: String, userInfo: UserProfile,updateAllowed: Boolean = true): Future[UserProfile]

  def mapPhoneWithUserId(phone: String, id: String): Future[Boolean]

  def getUserIdByPhone(phone: String): Future[Option[String]]

  def isExistPhone(phone: String): Future[Boolean]

  def deletePhone(phone: String): Future[Boolean]

  def mapEmailWithUserId(email: String, id: String): Future[Boolean]

  def deleteEmail(email: String): Future[Boolean]

  def isExistEmail(email: String): Future[Boolean]

  def getUserIdByEmail(email: String): Future[Option[String]]

  def isConfirmEmail(username: String): Future[Boolean]

}

case class UserProfileServiceImpl(caasService: CaasService,
                             profileRepository: UserProfileRepository) extends UserProfileService with Logging {

  override def isConfirmEmail(username: String): Future[Boolean] = async {
    profileRepository.getProfile(username).map(_.alreadyConfirmed)
  }

  override def addUserProfile(id: String, profile: UserProfile): Future[Boolean] = async {
    profileRepository.addProfile(profile.copy(
      username = id
    ))
  }

  override def getOrAddUserProfile(userId: String,
                                   newProfile: UserProfile,
                                   updateAllowed: Boolean = true): Future[UserProfile] = {
    for {
      oldProfile <- getUserProfile(userId)
      r <- oldProfile match {
        case Some(oldProfile) => updateProfile(userId, oldProfile, newProfile, updateAllowed)
        case _ => addUserProfile(userId, newProfile).flatMap({
          case true => Future.value(newProfile)
          case _ => Future.exception(InternalError(Some("Can't create this profile.")))
        })
      }
    } yield r
  }

  override def deleteUserProfile(id: String): Future[Boolean] = async {
    profileRepository.deleteProfile(id)
  }

  override def getUserProfileByEmail(email: String): Future[Option[UserProfile]] = async {
    profileRepository.getProfileByEmail(email)
  }

  override def getUserProfile(id: String): Future[Option[UserProfile]] = async {
    profileRepository.getProfile(id)
  }

  override def getUserProfiles(ids: Seq[String]): Future[Map[String, UserProfile]] = async {
    profileRepository.getProfiles(ids)
  }

  override def getAllUserProfiles(): Future[Map[String, UserProfile]] = async {
    profileRepository.getAllUserProfiles()
  }

  override def getActiveUserProfiles(from: Int, size: Int) = {
    for {
      listActiveUsername <- caasService.getActiveUsername(from, size).rescue { case e: Exception => futurePool(Pageable(0)) }
      mapProfile <- getUserProfiles(listActiveUsername.data)
      userProfiles = listActiveUsername.data.map(f => mapProfile.getOrElse(f, UserProfile(username = f)))
    } yield Pageable(listActiveUsername.total, userProfiles)
  }

  override def editUserProfile(request: EditUserProfileRequest) = {
    val id = request.username
    for {
      oldProfile <- getUserProfile(id).map(Utils.throwIfNotExist(_, Some("this profile is not exist.")))
      newProfile = request.build(oldProfile)
      r <- updateProfile(id, oldProfile, newProfile, true)
    } yield r
  }


  override def editUserSettings(request: EditUserSettingRequest): Future[Boolean] = {
    val id = request.username
    for {
      oldProfile <- getUserProfile(id).map(Utils.throwIfNotExist(_, Some("this profile is not exist.")))
      newProfile = request.build(oldProfile)
      r <- updateProfile(id, oldProfile, newProfile, true).map(_ => true)
    } yield r
  }

  private def updateProfile(userId: String,
                            old: UserProfile,
                            newProfile: UserProfile,
                            updateAllowed: Boolean = true): Future[UserProfile] = {
    var isUpdate = updateAllowed
    if (old.alreadyConfirmed != newProfile.alreadyConfirmed) {
      old.alreadyConfirmed = newProfile.alreadyConfirmed
      isUpdate = true
    }
    if (old.username == null || !old.username.equals(newProfile.username)) {
      old.username = userId
      isUpdate = true
    }
    if (old.avatar.isEmpty || (!old.avatar.equals(newProfile.avatar) && newProfile.avatar.isDefined)) {
      old.avatar = newProfile.avatar
      isUpdate = true
    }

    newProfile.firstName.foreach(x => old.firstName = x)
    newProfile.lastName.foreach(x => old.lastName = x)
    newProfile.fullName.foreach(x => old.fullName = x)
    newProfile.nationality.foreach(x => old.nationality = x)
    newProfile.nativeLanguages.foreach(x => old.nativeLanguages = x)
    newProfile.gender.foreach(x => old.gender = x)
    newProfile.dob.foreach(x => old.dob = x)
    newProfile.userSettings.foreach(x => old.userSettings = x)
    newProfile.createdTime.foreach(x => old.createdTime = x)
    old.updatedTime = Some(System.currentTimeMillis())

    for {
      isUpdate <- if (old.email.isEmpty && newProfile.email.isDefined) {
        old.email = newProfile.email.get
        mapEmailWithUserId(newProfile.email.get, userId).map(_ => true)
      } else Future.value(isUpdate)

      r <- if (isUpdate) addUserProfile(userId, old) else Future.True

    } yield r match {
      case true => old
      case _ => throw InternalError(Some("Can't update"))
    }
  }

  override def mapPhoneWithUserId(phoneNumber: String, id: String): Future[Boolean] = async {
    profileRepository.mapPhoneWithUserId(phoneNumber, id)
  }

  override def getUserIdByPhone(phoneNumber: String): Future[Option[String]] = async {
    profileRepository.getUserIdByPhone(phoneNumber)
  }

  override def isExistPhone(phoneNumber: String): Future[Boolean] = {
    getUserIdByPhone(phoneNumber).map(_.isDefined)
  }

  override def deletePhone(phoneNumber: String): Future[Boolean] = async {
    profileRepository.deletePhone(phoneNumber)
  }


  override def mapEmailWithUserId(email: String, id: String): Future[Boolean] = async {
    profileRepository.mapEmailWithUserId(email, id)
  }

  override def getUserIdByEmail(email: String): Future[Option[String]] = async {
    profileRepository.getUserIdByEmail(email)
  }

  override def isExistEmail(email: String): Future[Boolean] = {
    profileRepository.getUserIdByEmail(email).isDefined
  }

  override def deleteEmail(email: String): Future[Boolean] = async {
    profileRepository.deleteEmail(email)
  }

}