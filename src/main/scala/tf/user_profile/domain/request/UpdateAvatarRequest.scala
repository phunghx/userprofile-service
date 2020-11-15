package tf.user_profile.domain.request

import com.twitter.finagle.http.Request
import javax.inject.Inject
import tf.user_profile.domain.FileData

/**
 * @author anhlt
 */
case class UpdateAvatarRequest(data: FileData,
                               @Inject request: Request)
