package tf.user_profile.domain.profile

/**
 * Created by zkidkid on 10/1/16.
 */
case class UserInfo(username: String,
                    roles: Seq[Int] = Seq.empty,
                    isActive: Boolean,
                    createTime: Long)