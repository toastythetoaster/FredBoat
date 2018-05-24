package fredboat.util.extension

import com.fredboat.sentinel.entities.SendMessageResponse
import fredboat.sentinel.RawMessage
import fredboat.sentinel.TextChannel

fun SendMessageResponse.edit(textChannel: TextChannel, content: String) =
        textChannel.sentinel.editMessage(textChannel, messageId, RawMessage(content))