package fredboat.sentinel

import fredboat.perms.IPermissionSet
import org.springframework.stereotype.Service

@Suppress("unused")
@Service
private class SentinelProvider(sentinelParam: Sentinel) {
    init {
        providedSentinel = sentinelParam
    }
}

private lateinit var providedSentinel: Sentinel

interface SentinelEntity {
    val id: Long
    val idString: String
        get() = id.toString()
    val sentinel: Sentinel
        get() = providedSentinel
}

interface Channel : SentinelEntity {
    override val id: Long
    val name: String
    val guild: Guild
    val ourEffectivePermissions: Long
    fun checkOurPermissions(permissions: IPermissionSet): Boolean =
            ourEffectivePermissions and permissions.raw == permissions.raw
}

interface IMentionable {
    val asMention: String
}