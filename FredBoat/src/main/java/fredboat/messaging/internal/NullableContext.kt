package fredboat.messaging.internal

import com.fredboat.sentinel.entities.Embed
import com.fredboat.sentinel.entities.SendMessageResponse
import com.fredboat.sentinel.entities.embed
import fredboat.commandmeta.MessagingException
import fredboat.feature.I18n
import fredboat.sentinel.Guild
import fredboat.sentinel.Member
import fredboat.sentinel.TextChannel
import fredboat.sentinel.User
import fredboat.shared.constant.BotConstants
import fredboat.util.TextUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.text.MessageFormat
import java.util.*
import javax.annotation.CheckReturnValue

abstract class NullableContext {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Context::class.java)
    }

    /* Convenience properties */
    abstract val textChannel: TextChannel?
    abstract val guild: Guild?
    abstract val member: Member?
    abstract val user: User?

    // ********************************************************************************
    //                         Internal context stuff
    // ********************************************************************************

    private var i18n: ResourceBundle? = null


    // ********************************************************************************
    //                         Convenience reply methods
    // ********************************************************************************

    open fun replyMono(message: String): Mono<SendMessageResponse>? = textChannel?.send(message)
    fun reply(message: String) {
        replyMono(message)?.subscribe()
    }

    open fun replyMono(embed: Embed): Mono<SendMessageResponse>? = textChannel?.send(embed)
    fun reply(message: Embed) {
        replyMono(message)?.subscribe()
    }

    open fun replyWithNameMono(message: String): Mono<SendMessageResponse>? = replyMono(TextUtils.prefaceWithName(member, message))
    fun replyWithName(message: String) {
        replyWithNameMono(message)?.subscribe()
    }

    open fun replyWithMentionMono(message: String): Mono<SendMessageResponse>? = replyMono(TextUtils.prefaceWithMention(member, message))
    fun replyWithMention(message: String) {
        replyWithMentionMono(message)?.subscribe()
    }

    open fun replyImageMono(url: String, message: String = ""): Mono<SendMessageResponse>? {
        val embed = embedImage(url)
        embed.description = message
        return textChannel?.send(embed)
    }

    fun replyImage(url: String, message: String = "") {
        replyImageMono(url, message)?.subscribe()
    }

    fun sendTyping() {
        textChannel?.sendTyping()
    }

    open fun replyPrivateMono(message: String) = user?.sendPrivate(message)
    fun replyPrivate(message: String) {
        replyPrivateMono(message)?.subscribe()
    }

    /**
     * Return a single translated string.
     *
     * @param key Key of the i18n string.
     * @return Formatted i18n string, or a default language string if i18n is not found.
     */
    @CheckReturnValue
    fun i18n(key: String): String {
        return if (getI18n().containsKey(key)) {
            getI18n().getString(key)
        } else {
            log.warn("Missing language entry for key {} in language {}", key, I18n.getLocale(guild).code)
            I18n.DEFAULT.props.getString(key)
        }
    }

    /**
     * Return a translated string with applied formatting.
     *
     * @param key Key of the i18n string.
     * @param params Parameter(s) to be apply into the i18n string.
     * @return Formatted i18n string.
     */
    @CheckReturnValue
    fun i18nFormat(key: String, vararg params: Any): String {
        if (params.isEmpty()) {
            log.warn("Context#i18nFormat() called with empty or null params, this is likely a bug.",
                    MessagingException("a stack trace to help find the source"))
        }
        return try {
            MessageFormat.format(this.i18n(key), *params)
        } catch (e: IllegalArgumentException) {
            log.warn("Failed to format key '{}' for language '{}' with following parameters: {}",
                    key, getI18n().baseBundleName, params, e)
            //fall back to default props
            MessageFormat.format(I18n.DEFAULT.props.getString(key), *params)
        }

    }

    fun getI18n(): ResourceBundle {
        var result = i18n
        if (result == null) {
            result = I18n.get(guild)
            i18n = result
        }
        return result
    }

    private fun embedImage(url: String): Embed = embed {
        color = BotConstants.FREDBOAT_COLOR.rgb
        image = url
    }
}