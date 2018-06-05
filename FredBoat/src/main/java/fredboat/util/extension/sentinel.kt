package fredboat.util.extension

import com.fredboat.sentinel.entities.Embed
import com.fredboat.sentinel.entities.SendMessageResponse
import com.fredboat.sentinel.entities.footer
import fredboat.sentinel.Member
import fredboat.sentinel.RawMessage
import fredboat.sentinel.TextChannel
import kotlinx.coroutines.experimental.reactive.awaitSingle

fun SendMessageResponse.edit(textChannel: TextChannel, content: String) =
        textChannel.sentinel.editMessage(textChannel, messageId, RawMessage(content))

suspend fun Embed.addFooter(member: Member): Embed {
    footer {
        text = member.effectiveName.escapeMarkdown()
        iconUrl = member.info.awaitSingle().iconUrl
    }
    return this
}