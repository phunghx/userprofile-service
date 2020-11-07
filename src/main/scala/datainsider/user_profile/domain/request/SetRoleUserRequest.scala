package datainsider.user_profile.domain.request

/**
 * @author anhlt
 */
case class SetRoleUserRequest(username: String,
                              oldRoleIds: Set[Int] = Set.empty,
                              newRoleIds: Map[Int, Long])