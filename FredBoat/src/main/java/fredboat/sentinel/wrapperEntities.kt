package fredboat.sentinel

import com.fredboat.sentinel.entities.*
import fredboat.audio.lavalink.SentinelLavalink
import fredboat.audio.lavalink.SentinelLink
import fredboat.config.property.AppConfig
import fredboat.perms.IPermissionSet
import fredboat.perms.NO_PERMISSIONS
import fredboat.perms.Permission
import fredboat.perms.PermissionSet
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.stream.Stream
import kotlin.streams.toList

typealias RawGuild = com.fredboat.sentinel.entities.Guild
typealias RawMember = com.fredboat.sentinel.entities.Member
typealias RawUser = com.fredboat.sentinel.entities.User
typealias RawTextChannel = com.fredboat.sentinel.entities.TextChannel
typealias RawVoiceChannel = com.fredboat.sentinel.entities.VoiceChannel
typealias RawRole = com.fredboat.sentinel.entities.Role
typealias RawMessage = com.fredboat.sentinel.entities.Message

private val MEMBER_MENTION_PATTERN = Pattern.compile("<@!?([0-9]+)>", Pattern.DOTALL)
private val CHANNEL_MENTION_PATTERN = Pattern.compile("<#([0-9]+)>", Pattern.DOTALL)

@Service
private class WrapperEntityBeans(appConfigParam: AppConfig, lavalinkParam: SentinelLavalink) {
    init {
        appConfig = appConfigParam
        lavalink = lavalinkParam
    }
}

private lateinit var appConfig: AppConfig
private lateinit var lavalink: SentinelLavalink

// TODO: We need to update all channels if our permissions are changed

@Suppress("PropertyName")
abstract class Guild(raw: RawGuild) : SentinelEntity {

    override val id = raw.id

    protected lateinit var _name: String
    val name: String get() = _name

    protected var _owner: Member? = null // Discord has a history of null owners
    val owner: Member? get() = _owner

    protected var _members = ConcurrentHashMap<Long, Member>()
    val members: Map<Long, Member> get() = _members

    protected var _roles = ConcurrentHashMap<Long, Role>()
    val roles: Map<Long, Role> get() = _roles

    protected var _textChannels = ConcurrentHashMap<Long, TextChannel>()
    val textChannels: Map<Long, TextChannel> get() = _textChannels

    protected var _voiceChannels = ConcurrentHashMap<Long, VoiceChannel>()
    val voiceChannels: Map<Long, VoiceChannel> get() = _voiceChannels

    /* Helper properties */

    val selfMember: Member
        get() = _members[sentinel.getApplicationInfo().botId]!!
    val shardId: Int
        get() = ((id shr 22) % appConfig.shardCount.toLong()).toInt()
    val shardString: String
        get() = "[$shardId/${appConfig.shardCount}]"
    val link: SentinelLink
        get() = lavalink.getLink(this)
    val info: Mono<GuildInfo>
        get() = sentinel.getGuildInfo(this)
    /** This is true if we are present in this [Guild]*/
    val selfPresent: Boolean
        get() = true //TODO

    /** The routing key for the associated Sentinel */
    val routingKey: String
        get() = sentinel.tracker.getKey(shardId)

    fun getMember(id: Long): Member? = _members[id]
    fun getRole(id: Long): Role? = _roles[id]
    fun getTextChannel(id: Long): TextChannel? = _textChannels[id]
    fun getVoiceChannel(id: Long): VoiceChannel? = _voiceChannels[id]

    override fun equals(other: Any?): Boolean = other is Guild && id == other.id
    override fun hashCode() = id.hashCode()
}

/** Has public members we want to hide */
class InternalGuild(raw: RawGuild) : Guild(raw) {

    init {
        update(raw)
    }

    fun update(raw: RawGuild) {
        if (id != raw.id) throw IllegalArgumentException("Attempt to update $id with the data of ${raw.id}")
        _name = raw.name
        _members = raw.members.map { InternalMember(this, it) }.associateByTo(ConcurrentHashMap()) { it.id }
        _roles = raw.roles.map { Role(this, it) }.associateByTo(ConcurrentHashMap()) { it.id }
        _textChannels = raw.textChannels.map { InternalTextChannel(this, it) }.associateByTo(ConcurrentHashMap()) { it.id }
        _voiceChannels = raw.voiceChannels.map { VoiceChannel(this, it) }.associateByTo(ConcurrentHashMap()) { it.id }
        val rawOwner = raw.owner
        _owner = if (rawOwner != null) members[rawOwner] else null
    }

}

@Suppress("PropertyName")
abstract class Member(val guild: Guild, raw: RawMember) : IMentionable, SentinelEntity {
    override val id = raw.id
    val isBot = raw.bot

    protected lateinit var _name: String
    val name: String get() = _name

    protected var _discrim: Short = 0
    val discrim: Short get() = _discrim

    protected var _nickname: String? = null
    val nickname: String? get() = _nickname

    protected var _voiceChannel: Long? = null
    val voiceChannel: VoiceChannel? get() = _voiceChannel?.let { guild.getVoiceChannel(it) }

    protected var _roles = mutableListOf<Role>()
    val roles: List<Role> get() = _roles // Cast to immutable

    /* Convenience properties */
    val effectiveName: String get() = if (_nickname != null) _nickname!! else _name
    /** True if this [Member] is our bot */
    val isUs: Boolean get() = id == sentinel.getApplicationInfo().botId
    override val asMention: String get() = "<@$id>"
    val user: User
        get() = User(RawUser(
                id,
                name,
                discrim,
                isBot
        ))
    val info: Mono<MemberInfo> get() = sentinel.getMemberInfo(this)

    fun getPermissions(channel: Channel? = null): Mono<PermissionSet> {
        return when (channel) {
            null -> sentinel.checkPermissions(this, NO_PERMISSIONS)
                    .map { PermissionSet(it.effective) }
            else -> sentinel.checkPermissions(channel, this, NO_PERMISSIONS)
                    .map { PermissionSet(it.effective) }
        }
    }

    fun hasPermission(permissions: IPermissionSet, channel: Channel? = null): Mono<Boolean> {
        return when (channel) {
            null -> sentinel.checkPermissions(this, permissions)
                    .map { it.passed }
            else -> sentinel.checkPermissions(channel, this, permissions)
                    .map { it.passed }
        }
    }

    override fun equals(other: Any?): Boolean = other is Member && id == other.id
    override fun hashCode(): Int = id.hashCode()

}

class InternalMember(guild: Guild, raw: RawMember) : Member(guild, raw) {

    init {
        update(raw)
    }

    fun update(raw: RawMember) {
        if (id != raw.id) throw IllegalArgumentException("Attempt to update $id with the data of ${raw.id}")

        val newRoleList = mutableListOf<Role>()
        raw.roles.flatMapTo(newRoleList) {
            val role = guild.getRole(it)
            return@flatMapTo if (role != null) listOf(role) else emptyList()
        }
        _roles = newRoleList
        _name = raw.name
        _discrim = raw.discrim
        _nickname = raw.nickname
        _voiceChannel = raw.voiceChannel
    }

}

/** Note: This is not cached or subject to updates */
class User(val raw: RawUser) : IMentionable, SentinelEntity {
   override val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val discrim: Short
        get() = raw.discrim
    val isBot: Boolean
        get() = raw.bot
    override val asMention: String
        get() = "<@$id>"

    fun sendPrivate(message: String) = sentinel.sendPrivateMessage(this, RawMessage(message))
    fun sendPrivate(message: IMessage) = sentinel.sendPrivateMessage(this, message)

    override fun equals(other: Any?): Boolean {
        return other is User && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Suppress("PropertyName")
abstract class TextChannel(override val guild: Guild, raw: RawTextChannel) : Channel, IMentionable {
    override val id = raw.id

    protected lateinit var _name: String
    override val name: String get() = _name

    protected var _ourEffectivePermissions: Long = raw.ourEffectivePermissions
    override val ourEffectivePermissions: IPermissionSet get() = PermissionSet(_ourEffectivePermissions)

    override val asMention: String get() = "<#$id>"

    fun send(str: String): Mono<SendMessageResponse>
            = sentinel.sendMessage(guild.routingKey, this, RawMessage(str))
    fun send(message: IMessage): Mono<SendMessageResponse>
            = sentinel.sendMessage(guild.routingKey, this, message)
    fun editMessage(messageId: Long, message: String): Mono<Unit> =
            sentinel.editMessage(this, messageId, RawMessage(message))
    @Suppress("unused")
    fun editMessage(messageId: Long, message: IMessage): Mono<Unit> =
            sentinel.editMessage(this, messageId, message)
    fun deleteMessage(messageId: Long) = sentinel.deleteMessages(this, listOf(messageId))
    fun sendTyping() = sentinel.sendTyping(this)
    fun canTalk() = checkOurPermissions(Permission.VOICE_CONNECT + Permission.VOICE_SPEAK)


    override fun equals(other: Any?) = other is TextChannel && id == other.id
    override fun hashCode() = id.hashCode()
}

class InternalTextChannel(override val guild: Guild, raw: RawTextChannel) : TextChannel(guild, raw) {

    init {
        update(raw)
    }

    fun update(raw: RawTextChannel) {
        _name = raw.name
        _ourEffectivePermissions = raw.ourEffectivePermissions
    }

}

class VoiceChannel(override val guild: Guild, val raw: RawVoiceChannel) : Channel {
    override val id: Long
        get() = raw.id
    override val name: String
        get() = raw.name
    override val ourEffectivePermissions: IPermissionSet
        get() = PermissionSet(raw.ourEffectivePermissions)
    val userLimit: Int
        get() = raw.userLimit
    val members: List<Member>
        get() = listOf() //TODO

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

class Role(val guild: Guild, val raw: RawRole) : IMentionable, SentinelEntity {
    override val id: Long
        get() = raw.id
    val name: String
        get() = raw.name
    val permissions: PermissionSet
        get() = PermissionSet(raw.permissions)
    val isPublicRole: Boolean // The @everyone role shares the ID of the guild
        get() = id == guild.id
    override val asMention: String
        get() = "<@$id>"
    val info: Mono<RoleInfo>
        get() = sentinel.getRoleInfo(this)

    override fun equals(other: Any?): Boolean {
        return other is Role && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class Message(val guild: Guild, val raw: MessageReceivedEvent) : SentinelEntity {
    override val id: Long
        get() = raw.id
    val content: String
        get() = raw.content
    val member: Member // Maybe make this nullable?
        get() = guild.getMember(raw.id)!!
    val channel: TextChannel // Maybe make this nullable?
        get() = guild.getTextChannel(raw.channel.id)!!
    val mentionedMembers: List<Member>
    // Technically one could mention someone who isn't a member of the guild,
    // but we don't really care for that
        get() = MEMBER_MENTION_PATTERN.matcher(content)
                .results()
                .flatMap<Member> {
                    Stream.ofNullable(guild.getMember(it.group(1).toLong()))
                }
                .toList()
    val mentionedChannels: List<TextChannel>
        get() = CHANNEL_MENTION_PATTERN.matcher(content)
                .results()
                .flatMap<TextChannel> {
                    Stream.ofNullable(guild.getTextChannel(it.group(1).toLong()))
                }
                .toList()
    val attachments: List<String>
        get() = raw.attachments

    fun delete(): Mono<Unit> = sentinel.deleteMessages(channel, listOf(id))
}
