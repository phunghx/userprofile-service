package datainsider.user_profile.domain.request

import com.twitter.finagle.http.Request
import javax.inject.Inject

/**
 * @author anhlt
 */
case class SetOAuthPasswordRequest(password: String, @Inject request: Request)
