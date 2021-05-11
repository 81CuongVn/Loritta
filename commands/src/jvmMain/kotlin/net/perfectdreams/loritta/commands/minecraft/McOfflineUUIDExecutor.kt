package net.perfectdreams.loritta.commands.minecraft

import net.perfectdreams.loritta.common.commands.CommandArguments
import net.perfectdreams.loritta.common.commands.CommandContext
import net.perfectdreams.loritta.common.commands.CommandExecutor
import net.perfectdreams.loritta.common.commands.declarations.CommandExecutorDeclaration
import net.perfectdreams.loritta.common.commands.options.CommandOptions
import net.perfectdreams.loritta.common.emotes.Emotes
import net.perfectdreams.loritta.common.locale.LocaleKeyData
import java.util.*

class McOfflineUUIDExecutor(val emotes: Emotes) : CommandExecutor() {
    companion object : CommandExecutorDeclaration(McOfflineUUIDExecutor::class) {
        object Options : CommandOptions() {
            val username = string("player_name", LocaleKeyData("commands.category.minecraft.playerNameJavaEdition"))
                .register()
        }

        override val options = Options
    }

    override suspend fun execute(context: CommandContext, args: CommandArguments) {
        val player = args[Options.username]

        val uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:$player").toByteArray(Charsets.UTF_8))

        context.sendReply(context.locale["commands.command.mcofflineuuid.result", player, uuid.toString()])
    }
}