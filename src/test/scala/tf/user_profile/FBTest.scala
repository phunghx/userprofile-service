package tf.user_profile

import com.twitter.inject.Test
import org.apache.commons.codec.digest.HmacUtils
import tf.user_profile.util.ZConfig


/**
  * @author anhlt
  */
class FBTest extends Test {

  test( "Create app secret proof") {
    val token = "EAAF83ptqtawBAPwKZBYXpOO4kTYZBAc1BwCUZByBynDiKvOPYm42VfG77tAsTfZAc7OAkss1a04HXp9Q8ZAbZBtmOZATfHQ8rqBWZBZBXZBDBpVC9qQjWqbY8jXVkctWPXxHfWD8s05HalRmOhBIGhXBBeCAGOLYUEd439jLLWQVcQmi3G2IBZAT6dDR4SxamhtLmbAsZB3aPWWMo4bHNxMRDQq6jriZAw3fK8jsZD"
    val appSecret = ZConfig.getString("db.oauth.facebook.app_secret", null)
    val appSecretProof = HmacUtils.hmacSha256Hex(appSecret, token)

    println(s"Appprof: ${appSecretProof}")

  }

}