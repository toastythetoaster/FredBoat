package fredboat.util

import fredboat.sentinel.IPermissionSet

class InsufficientPermissionException(
        val permissions: IPermissionSet,
        override val message: String = "Missing permissions: $permissions"
) : RuntimeException()