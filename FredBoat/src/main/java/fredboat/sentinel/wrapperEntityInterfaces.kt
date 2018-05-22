package fredboat.sentinel

interface Channel {
    val id: Long
    val name: String
    val guild: Guild
    val ourEffectivePermissions: Long
}

interface IMentionable {
    val asMention: String
}