package datainsider.user_profile.domain.request

import com.twitter.finagle.http.Request
import datainsider.user_profile.domain.FileData
import javax.inject.Inject

/**
 * @author anhlt
 */
case class UpdateAvatarRequest(data: FileData,
                               @Inject request: Request)
