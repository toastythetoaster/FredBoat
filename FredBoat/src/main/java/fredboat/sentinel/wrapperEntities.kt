package fredboat.sentinel

import com.fredboat.sentinel.entities.IMessage
import com.fredboat.sentinel.entities.MessageReceivedEvent
import com.fredboat.sentinel.entities.SendMessageResponse
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.config.property.AppConfig
import fredboat.perms.IPermissionSet
import fredboat.perms.NO_PERMISSIONS
import fredboat.perms.Permission
import fredboat.perms.PermissionSet
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.regex.Pattern

typealias RawGuild = com.fredboat.sentinel.entities.Guild
typealias RawMember = com.fredboat.sentinel.entities.Member
typealias RawUser = com.fredboat.sentinel.entities.User
typealias RawTextChannel = com.fredboat.sentinel.entities.TextChannel
typealias RawVoiceChannel = com.fredboat.sentinel.entities.VoiceChannel
typealias RawRole = com.fredboat.sentinel.entities.Role
typealias RawMesssage = com.fredboat.sentinel.entities.Message

private val MENTION_PATTERN = Pattern.compile("<@!?([0-9]+)>", Pattern.DOTALL)

// TODO: These classes are rather inefficient. We should cache more things, and we should avoid duplication of Guild entities

@Service
class EntityWrapperBeanProvider(sentinelParam: Sentinel, appConfigParam: AppConfig) {
    companion object {
        lateinit var sentinel: Sentinel
        lateinit var appConfig: AppConfig
    }

    init {
        sentinel = sentinelParam
        appConfig = appConfigParam
    }

}

class Guild(
        val id: Long
) {
    val raw: RawGuild
        get() = Sentinel.INSTANCE.getGuild(id)
    val name: String
        get() = raw.name
    val owner: Member?
        get() {
            if (raw.owner != null) return Member(raw.owner!!)
            return null
        }

    // TODO: Make these lazy so we don't have to recompute them
    val textChannels: List<TextChannel>
        get() = raw.textChannels.map { TextChannel(it, id) }
    val voiceChannels: List<VoiceChannel>
        get() = raw.voiceChannels.map { VoiceChannel(it, id) }
    val voiceChannelsMap: Map<Long, VoiceChannel>
        get() = voiceChannels.associateBy { it.id }
    val selfMember: Member
        get() = membersMap[Sentinel.INSTANCE.getApplicationInfo().botId]!!
    val members: List<Member>
        get() = raw.members.map { Member(it.value) }
    val membersMap: Map<Long, Member>
        get() = members.associateBy { it.id }
    val roles: List<Role>
        get() = raw.roles.map { Role(it, id) }
    val shardId: Int
        get() = ((id shr 22) % EntityWrapperBeanProvider.appConfig.shardCount.toLong()).toInt()

    /** This is true if we are present in this [Guild]*/
    val selfPresent: Boolean
        get() = true //TODO

    fun getTextChannel(id: Long): TextChannel? {
        textChannels.forEach { if (it.id == id) return it }
        return null
    }

    fun getVoiceChannel(id: Long): VoiceChannel? {
        voiceChannels.forEach { if (it.id == id) return it }
        return null
    }

    fun getRole(id: Long): Role? {
        roles.forEach { if(it.id == id) return it }
        return null
    }

    override fun equals(other: Any?): Boolean {
        return other is Guild && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun getMember(userId: Long): Member? = membersMap[userId]
}

class Member(val raw: RawMember) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val nickname: String?
        get() = raw.nickname
    val effectiveName: String
        get() = if (raw.nickname != null) raw.nickname!! else raw.name
    val discrim: Short
        get() = raw.discrim
    val guild: Guild
        get() = Guild(raw.guildId)
    val guildId: Long
        get() = raw.guildId
    val isBot: Boolean
        get() = raw.bot
    val voiceChannel: VoiceChannel?
        get() {
            if (raw.voiceChannel != null) return guild.getVoiceChannel(raw.voiceChannel!!)
            return null
        }
    val roles: List<Role>
        get() {
            val list = mutableListOf<Role>()
            val guildRoles = guild.roles
            guildRoles.forEach { if (raw.roles.contains(it.id)) list.add(it) }
            return list.toList()
        }
    /** True if this [Member] is our bot */
    val isUs: Boolean
        get() = id == Sentinel.INSTANCE.getApplicationInfo().botId

    fun asMention() = "<@$id>"
    fun asUser(): User {
        return User(RawUser(
                id,
                name,
                discrim,
                isBot
        ))
    }

    fun getPermissions(channel: Channel? = null): Mono<PermissionSet> {
        return when (channel) {
            null -> Sentinel.INSTANCE.checkPermissions(this, NO_PERMISSIONS)
                    .map { PermissionSet(it.effective) }
            else -> Sentinel.INSTANCE.checkPermissions(channel, this, NO_PERMISSIONS)
                    .map { PermissionSet(it.effective) }
        }
    }

    fun hasPermission(permissions: IPermissionSet, channel: Channel? = null): Mono<Boolean> {
        return when (channel) {
            null -> Sentinel.INSTANCE.checkPermissions(this, permissions)
                    .map { it.passed }
            else -> Sentinel.INSTANCE.checkPermissions(channel, this, permissions)
                    .map { it.passed }
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is Member && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class User(val raw: RawUser) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val discrim: Short
        get() = raw.discrim
    val bot: Boolean
        get() = raw.bot

    fun sendPrivate(message: String)
            = Sentinel.INSTANCE.sendPrivateMessage(this, RawMesssage(message))
    fun sendPrivate(message: IMessage)
            = Sentinel.INSTANCE.sendPrivateMessage(this, message)

    override fun equals(other: Any?): Boolean {
        return other is User && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

interface Channel {
    val id: Long
    val name: String
    val guild: Guild
    val ourEffectivePermissions: Long
}

class TextChannel(val raw: RawTextChannel, val guildId: Long) : Channel {
    override val id: Long
        get() = raw.id
    override val name: String
        get() = raw.name
    override val guild: Guild
        get() = Guild(guildId)
    override val ourEffectivePermissions: Long
        get() = raw.ourEffectivePermissions

    fun checkOurPermissions(permissions: IPermissionSet): Boolean =
            raw.ourEffectivePermissions and permissions.raw == permissions.raw

    fun send(str: String): Mono<SendMessageResponse> {
        return Sentinel.INSTANCE.sendMessage(raw, RawMesssage(str))
    }

    fun send(message: IMessage): Mono<SendMessageResponse> {
        return Sentinel.INSTANCE.sendMessage(raw, message)
    }

    fun sendTyping() {
        Sentinel.INSTANCE.sendTyping(raw)
    }

    override fun equals(other: Any?): Boolean {
        return other is TextChannel && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun canTalk() = checkOurPermissions(Permission.VOICE_CONNECT + Permission.VOICE_SPEAK)
}

class VoiceChannel(val raw: RawVoiceChannel, val guildId: Long) : Channel {
    override val id: Long
        get() = raw.id
    override val name: String
        get() = raw.name
    override val guild: Guild
        get() = Guild(guildId)
    override val ourEffectivePermissions: Long
        get() = raw.ourEffectivePermissions
    val userLimit: Int
        get() = 0 //TODO
    val members: List<Member>
        get() = listOf() //TODO: List of members

    override fun equals(other: Any?): Boolean {
        return other is VoiceChannel && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun connect() {
        SentinelLavalink.INSTANCE.getLink(guild).connect(this)
    }
}

class Role(val raw: RawRole, val guildId: Long) {
    val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val permissions: PermissionSet
        get() = PermissionSet(raw.permissions)
    val guild: Guild
        get() = Guild(guildId)
    val publicRole: Boolean // The @everyone role shares the ID of the guild
        get() = id == guildId

    override fun equals(other: Any?): Boolean {
        return other is Role && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class Message(val raw: MessageReceivedEvent) {
    val id: Long
        get() = raw.id
    val content: String
        get() = raw.content
    val member: Member
        get() = Member(raw.author)
    val guild: Guild
        get() = Guild(raw.guildId)
    val channel: TextChannel
        get() = TextChannel(raw.channel, raw.guildId)
    val mentionedMembers: List<Member>
        get() {
            // Technically one could mention someone who isn't a member of the guild,
            // but we don't really care for that

            val matcher = MENTION_PATTERN.matcher(content)
            val list =  mutableListOf<Member>()
            val members = guild.membersMap
            while (matcher.find()) {
                members[matcher.group(1).toLong()]?.let { list.add(it) }
            }

            return list
        }

    fun delete(): Mono<Unit> = Sentinel.INSTANCE.deleteMessages(channel, listOf(id))
}