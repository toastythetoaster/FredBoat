package fredboat.db.transfer

import fredboat.definitions.PermissionLevel

data class PermissionEntity(
        var adminList: List<Long> = emptyList(),
        var djList: List<Long> = emptyList(),
        var userList: List<Long> = emptyList()
) {
    fun fromEnum(level: PermissionLevel): List<Long> {
        return when (level) {
            PermissionLevel.ADMIN -> adminList
            PermissionLevel.DJ -> djList
            PermissionLevel.USER -> userList
            else -> throw IllegalArgumentException("Unexpected enum $level")
        }
    }

    fun fromEnum(level: PermissionLevel, newList: List<Long>) {
        when (level) {
            PermissionLevel.ADMIN -> adminList = newList
            PermissionLevel.DJ -> djList = newList
            PermissionLevel.USER -> userList = newList
            else -> throw IllegalArgumentException("Unexpected enum $level")
        }
    }
}