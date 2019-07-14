package fredboat.command.music.control

import fredboat.commandmeta.abs.Command
import fredboat.commandmeta.abs.CommandContext
import fredboat.commandmeta.abs.ICommandRestricted
import fredboat.definitions.PermissionLevel
import fredboat.messaging.internal.Context
import lavalink.client.io.filters.Timescale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * TODO: Fix track timers when changing speed. Also add I18n.
 */
class TimescaleCommand(name: String, vararg aliases: String) : Command(name, *aliases), ICommandRestricted {

    override val minimumPerms = PermissionLevel.BOT_ADMIN
    private val range = 50..200

    override suspend fun invoke(context: CommandContext) {
        val player = context.guild.getOrCreateGuildPlayer()

        if (!context.hasArguments()) {
            printTimescale(context, player.filters.timescale ?: Timescale())
            return
        }

        val timescale = player.filters.timescale ?: Timescale()

        var speed = timescale.speed
        var pitch = timescale.pitch
        var rate = timescale.rate

        for (i in 0 until min(3, context.args.size)) {
            var v = context.args[i].toIntOrNull()
            if (v == null) {
                context.sendHelpAsync()
                return
            }
            v = max(range.first, min(range.last, v))
            val vFloat = v.toFloat() / 100
            when (i) {
                0 -> speed = vFloat
                1 -> pitch = vFloat
                2 -> rate = vFloat
            }
        }

        timescale.speed = speed
        timescale.pitch = pitch
        timescale.rate = rate

        player.filters.setTimescale(timescale).commit()

        printTimescale(context, player.filters.timescale!!)
    }

    private fun printTimescale(context: Context, ts: Timescale) {
        context.reply("Speed: ${ts.speed.format()}\n"
                + "Pitch: ${ts.pitch.format()}\n"
                + "Rate: ${ts.rate.format()}")
    }

    private fun Float.format() = (this * 100).roundToInt()

    override fun help(context: Context): String {
        return "{0}{1} <50-200> [50-200] [50-200]\n#Sets the speed, pitch, and rate respectively. 100 is normal, 200 is double."
    }

}