package tf.user_profile.controller.http.filter.parser

import java.util.UUID

import com.fasterxml.jackson.databind.node.ObjectNode
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finatra.validation.NotEmpty
import com.twitter.inject.Logging
import com.twitter.util.Future
import datainsider.user_profile.domain.profile.UserProfile
import datainsider.user_profile.util.Utils
import javax.inject.Inject
import tf.user_profile.controller.http.filter.email.EmailFilterRequest
import tf.user_profile.domain.profile.UserProfile
import tf.user_profile.domain.request.RegisterUserBodyRequest
import tf.user_profile.util.{Configs, JsonParser, Utils}

/**
  * @author anhlt
  */

case class UserRegisterRequest(@NotEmpty email: String,
                               @NotEmpty password: String,
                               @NotEmpty fullName: String,
                               gender: Option[Int] = None,
                               dob: Option[Long] = None,
                               nationality: Option[String] = None,
                               nativeLanguages: Option[Seq[String]] = None) extends EmailFilterRequest {

  override def getEmail(): String = email

  def build(isConfirmed: Option[Boolean] = Some(false)) = {
    UserProfile(
      username =  s"up-${UUID.randomUUID().toString}",
      email = Some(email),
      fullName = Some(fullName),
      alreadyConfirmed = isConfirmed.getOrElse(false),
      gender = gender,
      dob = dob,
      nationality = nationality,
      nativeLanguages = nativeLanguages.flatMap(x => if(x.isEmpty) None else Some(x)),
      updatedTime = Some(System.currentTimeMillis()),
      createdTime = Some(System.currentTimeMillis())
    )
  }

}

case class EditUserProfileRequest(fullName: Option[String],
                                  gender: Option[Int],
                                  dob: Option[Long],
                                  nationality: Option[String] ,
                                  nativeLanguages: Option[Seq[String]],
                                  userSettings: Option[ObjectNode],
                                  avatar: Option[String] ,
                                  @Inject request: Request )  {

  import tf.user_profile.controller.http.filter.user.UserContext._

  def username = request.user.username.get

  def build(oldProfile: UserProfile) = {
    val profile = UserProfile(
      username = request.user.username.get,
      email = oldProfile.email,
      fullName = if (fullName.isDefined) fullName else oldProfile.fullName,
      gender = if (gender.isDefined) gender else oldProfile.gender,
      dob = if (dob.isDefined) dob else oldProfile.dob,
      nationality = if (nationality.isDefined) nationality else oldProfile.nationality,
      nativeLanguages = if (nativeLanguages.isDefined) nativeLanguages else oldProfile.nativeLanguages,
      alreadyConfirmed = oldProfile.alreadyConfirmed,
      avatar = if (avatar.isDefined) avatar else oldProfile.avatar,
      updatedTime = Some(System.currentTimeMillis()),
      createdTime = None
    )

    val settings: Map[String, Any] = {
      var settings = (if (userSettings.isDefined) userSettings else oldProfile.userSettings)
        .map(x => JsonParser.fromJson[Map[String, Any]](x.toString))
        .getOrElse(Map.empty)

      if(!settings.contains("nationality")) {
        settings = settings ++ Map(
          "nationality" -> profile.nationality
        ).filter(_._2.isDefined)
      }

      if(!settings.contains("native_languages")) {
        settings = settings ++ Map(
          "native_languages" -> profile.nativeLanguages
        )
      }

      settings
    }

    profile.copy(
      nationality = if (settings.contains("nationality"))
        settings.get("nationality").map(_.toString)
      else profile.nationality,

      nativeLanguages = if(settings.contains("native_languages"))
        settings.get("native_languages").map(_.asInstanceOf[Seq[String]])
      else profile.nativeLanguages,
      userSettings = Some(JsonParser.toJsonNode(settings))
    )
  }

}


case class EditUserSettingRequest(userSettings: Option[ObjectNode],
                                  @Inject request: Request )  {

  import tf.user_profile.controller.http.filter.user.UserContext._

  def username = request.user.username.get

  def build(oldProfile: UserProfile) = {
    import scala.collection.JavaConversions._

    val profile = oldProfile.copy(

      nationality = if (userSettings.isDefined)
        userSettings.map(_.at("/nationality").asText(null))
          .filter(_ != null)
      else oldProfile.nationality,
      nativeLanguages = if (userSettings.isDefined)
        userSettings.map(_.at("/native_languages")
          .elements()
          .map(_.asText(null))
          .filter(_ != null)
          .toSeq)
      else oldProfile.nativeLanguages,
      updatedTime = Some(System.currentTimeMillis()),
      createdTime = None
    )

    val settings: Map[String, Any] = {
      val settings = if (userSettings.isDefined) userSettings else oldProfile.userSettings
      settings
        .map(x => JsonParser.fromJson[Map[String, Any]](x.toString))
        .getOrElse(Map.empty) ++ Map(
        "nationality" -> profile.nationality,
        "native_languages" -> profile.nativeLanguages
      )
    }
    profile.copy(userSettings = Some(JsonParser.toJsonNode(settings)))
  }

}

case class MultiGetUserProfileRequest(usernames: Seq[String],
                                      @Inject request: Request )


case class RegisterUserParser @Inject()() extends SimpleFilter[Request, Response] with Logging {

  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val bodyRequest = JsonParser.fromJson[RegisterUserBodyRequest](request.contentString)
    val restrictEmail = Configs.getRestrictEmail("default", "")
    if (!Utils.isValidEmailV1(bodyRequest.email, restrictEmail)) {
      throw new UnsupportedOperationException("Unsupported your email domain")
    }
    DataRequestContext.setDataRequest(request, UserRegisterRequest(bodyRequest.email,
      bodyRequest.password,
      bodyRequest.fullName)
    )
    service(request)
  }
}



