package datainsider.user_profile.domain.profile

import com.fasterxml.jackson.databind.node.ObjectNode
import datainsider.user_profile.util.JsonParser

/**
 * @author anhlt
 */
object UserGender {
  val Other = -1
  val Female = 0
  val Male = 1
}

case class UserProfile(var username: String = "",
                       var alreadyConfirmed: Boolean = true,
                       var fullName: Option[String] = None,
                       var lastName: Option[String] = None,
                       var firstName: Option[String] = None,
                       var email: Option[String] = None,
                       var phone: Option[String] = None,
                       var gender: Option[Int] = None,
                       var dob: Option[Long] = None,
                       var nationality: Option[String] = None,
                       var nativeLanguages: Option[Seq[String]] = None,
                       var avatar: Option[String] = None,
                       oauthType: Option[String] = None,
                       additionalInfo: Option[Map[String, String]] = None,
                       var userSettings: Option[ObjectNode] = None,
                       var updatedTime: Option[Long] = None,
                       var createdTime: Option[Long] = None)

object UserProfile {
  implicit class UserProfileLike(userProfile: UserProfile) {

    def toHubspotJSONStr() : String = {
      val props = Map("properties" -> Seq(
        Map("property" -> "xed_source", "value" -> "REGISTER"),
        Map("property" -> "xed_user_id", "value" -> userProfile.username),
        Map("property" -> "email", "value" -> userProfile.email.getOrElse("")),
        Map("property" -> "firstname", "value" -> userProfile.firstName.getOrElse(userProfile.fullName.getOrElse(""))),
        Map("property" -> "lastname", "value" -> userProfile.lastName.getOrElse("")),
        Map("property" -> "phone", "value" -> userProfile.phone.getOrElse(""))
      ))
      JsonParser.toJson(props, pretty = false)
    }

    def toRegisterFBJSONStr() : String = {
      val props = Map(
        "action" ->"REGISTER",
        "source" -> "fb",
        "xed_user_id" -> userProfile.username,
        "email" -> userProfile.email.getOrElse(""),
        "first_name"-> userProfile.firstName.getOrElse(userProfile.fullName.getOrElse("")),
        "last_name"-> userProfile.lastName.getOrElse(""),
        "full_name" -> userProfile.fullName.getOrElse(""),
        "phone" -> userProfile.phone.getOrElse("")
      )
      JsonParser.toJson(props, pretty = false)
    }

    def toRegisterJSONStr() : String = {
      val props = Map(
        "action" ->"REGISTER",
        "source" -> "email_password",
        "xed_user_id" -> userProfile.username,
        "email" -> userProfile.email.getOrElse(""),
        "first_name"-> userProfile.firstName.getOrElse(userProfile.fullName.getOrElse("")),
        "last_name"-> userProfile.lastName.getOrElse(""),
        "full_name" -> userProfile.fullName.getOrElse(""),
        "phone" -> userProfile.phone.getOrElse("")
      )
      JsonParser.toJson(props, pretty = false)
    }
  }
}