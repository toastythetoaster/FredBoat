package fredboat.util.extension

import com.fredboat.sentinel.entities.Embed
import com.fredboat.sentinel.entities.SendMessageResponse
import com.fredboat.sentinel.entities.footer
import fredboat.sentinel.Member
import fredboat.sentinel.RawMessage
import fredboat.sentinel.TextChannel

fun SendMessageResponse.edit(textChannel: TextChannel, content: String) =
        textChannel.sentinel.editMessage(textChannel, messageId, RawMessage(content))

fun Embed.addFooter(member: Member): Embed {
    footer {
        text = member.effectiveName.escapeMarkdown()
        iconUrl = member.avatarUrl
    }
    return this
}