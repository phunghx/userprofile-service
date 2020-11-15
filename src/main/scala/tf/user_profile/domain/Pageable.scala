package tf.user_profile.domain

/**
 * @author anhlt
 */
case class Pageable[T](total: Long, data: Seq[T] = Nil)