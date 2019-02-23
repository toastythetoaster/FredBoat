/*
 *
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package fredboat.messaging.internal

import com.fredboat.sentinel.entities.Embed
import com.fredboat.sentinel.entities.SendMessageResponse
import com.fredboat.sentinel.entities.passed
import fredboat.command.config.PrefixCommand
import fredboat.perms.IPermissionSet
import fredboat.perms.PermissionSet
import fredboat.perms.PermsUtil
import fredboat.sentinel.*
import fredboat.util.TextUtils
import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import javax.annotation.CheckReturnValue

/**
 * Provides a context to whats going on. Where is it happening, who caused it?
 * Also home to a bunch of convenience methods
 */
abstract class Context : NullableContext() {

    abstract override val textChannel: TextChannel
    abstract override val guild: Guild
    abstract override val member: Member
    abstract override val user: User

    /* Convenience properties */
    val prefix: String get() = PrefixCommand.giefPrefix(guild)
    val selfMember: Member get() = guild.selfMember
    val sentinel: Sentinel get() = guild.sentinel
    val routingKey: String get() = guild.routingKey

    // ********************************************************************************
    //                         Convenience reply methods
    // ********************************************************************************


    fun replyMono(message: String): Mono<SendMessageResponse> = textChannel.send(message)

    fun reply(message: String) {
        textChannel.send(message).subscribe()
    }

    fun replyMono(message: Embed): Mono<SendMessageResponse> = textChannel.send(message)

    fun reply(message: Embed) {
        textChannel.send(message).subscribe()
    }

    fun replyWithNameMono(message: String): Mono<SendMessageResponse> {
        return replyMono(TextUtils.prefaceWithName(member, message))
    }

    fun replyWithName(message: String) {
        reply(TextUtils.prefaceWithName(member, message))
    }

    fun replyWithMentionMono(message: String): Mono<SendMessageResponse> {
        return replyMono(TextUtils.prefaceWithMention(member, message))
    }

    fun replyWithMention(message: String) {
        reply(TextUtils.prefaceWithMention(member, message))
    }

    fun replyImageMono(url: String, message: String = ""): Mono<SendMessageResponse> {
        val embed = embedImage(url)
        embed.description = message
        return textChannel.send(embed)
    }

    fun replyImage(url: String, message: String = "") {
        replyImageMono(url, message).subscribe()
    }

    fun sendTyping() {
        textChannel.sendTyping()
    }

    /* Private messages */
    /**
     * Privately DM the invoker
     */
    fun replyPrivateMono(message: String) = user.sendPrivate(message)
    /**
     * Privately DM the invoker
     */
    fun replyPrivate(message: String) {
        user.sendPrivate(message).subscribe()
    }

    /**
     * Checks whether we have the provided permissions for the channel of this context
     */
    @CheckReturnValue
    fun hasPermissions(permissions: IPermissionSet): Boolean = textChannel.checkOurPermissions(permissions)

    /**
     * Checks whether we have the provided permissions for the provided channel
     */
    @CheckReturnValue
    fun hasPermissions(tc: TextChannel, permissions: IPermissionSet): Boolean {
        return tc.checkOurPermissions(permissions)
    }

    /**
     * @return true if we the bot have all the provided permissions, false if not. Also informs the invoker about the
     * missing permissions for the bot, given there is a channel to reply in.
     */
    suspend fun checkSelfPermissionsWithFeedback(permissions: IPermissionSet): Boolean {
        val result = guild.sentinel.checkPermissions(guild.selfMember, permissions).awaitSingle()
        if (result.passed) return true
        if (result.missingEntityFault) return false // Error

        val builder = StringBuilder()
        PermissionSet(result.missing).asList().forEach{
            builder.append(it.uiName).append("**, **")
        }

        // Remove the dangling last characters
        val str = builder.toString().substring(0, builder.length - 6)

        reply("${i18n("permissionMissingBot")} **$str**")
        return false
    }

    /**
     * @return true if the invoker has all the provided permissions, false if not. Also informs the invoker about the
     * missing permissions, given there is a channel to reply in.
     */
    suspend fun checkInvokerPermissionsWithFeedback(permissions: IPermissionSet): Boolean {
        if (member.isOwner()) return true
        val result = guild.sentinel.checkPermissions(member, permissions).awaitSingle()

        if (result.passed) return true
        if (result.missingEntityFault) return false // Error

        val builder = StringBuilder()
        PermissionSet(result.missing).asList().forEach{
            builder.append(it.uiName).append("**, **")
        }

        // Remove the dangling last characters
        val str = builder.toString().substring(0, builder.length - 6)

        reply("${i18n("permissionMissingInvoker")} **$str**")
        return false
    }

    suspend fun memberLevel() = PermsUtil.getPerms(member)
}
