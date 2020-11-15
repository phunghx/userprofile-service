package tf.user_profile.controller.http.filter.phone

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future
import tf.user_profile.controller.http.filter.parser.DataRequestContext._
import javax.inject.Inject
import tf.user_profile.exception.EmailExistedError
import tf.user_profile.service.UserProfileService
import tf.user_profile.service.verification.VerifyService

/**
  * @author anhlt
  */

trait PhoneFilterRequest {
  def getPhone(): Option[String]
}

class  ExistPhoneFilter @Inject()(phoneAccountService: VerifyService, userProfileService: UserProfileService) extends SimpleFilter[Request, Response] {
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    val phoneRequest: PhoneFilterRequest = request.requestData
    phoneRequest.getPhone() match {
      case Some(x) => userProfileService.isExistPhone(x).flatMap({
        case false => service(request)
        case _ => Future.exception(new EmailExistedError)
      })
      case _ => service(request)
    }
    //    userProfileService.isExistPhoneNumber(phoneRequest.getPhone()).flatMap({
    //      case false => service(request)
    //      case _ => Future.exception(new AlreadyExistPhoneException)
    //    })
  }
}

//class NotExistPhoneFilter @Inject()(
//                                     phoneAccountService: VerifyPhoneNumberService, userProfileService: UserProfileService) extends SimpleFilter[Request, Response] {
//  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
//    val phoneRequest: PhoneFilterRequest = request.requestData
//    userProfileService.isExistPhoneNumber(phoneRequest.getPhone()).flatMap({
//      case false => Future.exception(new NotExistPhoneException)
//      case _ => service(request)
//    })
//  }
//}