package net.perfectdreams.loritta.commands.misc

import net.perfectdreams.loritta.common.commands.CommandArguments
import net.perfectdreams.loritta.common.commands.CommandContext
import net.perfectdreams.loritta.common.commands.CommandExecutor
import net.perfectdreams.loritta.common.commands.declarations.CommandExecutorDeclaration
import net.perfectdreams.loritta.common.commands.options.CommandOptions
import net.perfectdreams.loritta.common.locale.LocaleKeyData

class PingAyayaExecutor : CommandExecutor() {
    companion object : CommandExecutorDeclaration(PingAyayaExecutor::class) {
        object Options : CommandOptions() {
            val ayayaCount = integer("ayaya_count", LocaleKeyData("commands.command.ping.ayaya.count"))
                .register()

            val boldAyayaCount = integer("bold_ayaya_count", LocaleKeyData("commands.command.ping.ayaya.count"))
                .optional()
                .register()
        }

        override val options = Options
    }

    override suspend fun execute(context: CommandContext, args: CommandArguments) {
        val ayayaCount = args[options.ayayaCount]
        val boldAyayaCount = args[options.boldAyayaCount] ?: 1

        context.sendMessage("${(0 until ayayaCount).map { "ayaya" }.joinToString(" ")}! **${(0 until boldAyayaCount).map { "ayaya" }.joinToString(" ")}!** https://tenor.com/view/ayaya-yeah-happy-hapinness-kiniro-gif-12992329")
    }
}