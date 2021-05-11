package net.perfectdreams.loritta.commands.images

import net.perfectdreams.loritta.commands.images.base.GabrielaImageServerSingleCommandBase
import net.perfectdreams.loritta.commands.images.base.SingleImageOptions
import net.perfectdreams.loritta.common.commands.declarations.CommandExecutorDeclaration
import net.perfectdreams.loritta.common.emotes.Emotes
import net.perfectdreams.loritta.common.utils.gabrielaimageserver.GabrielaImageServerClient

class EdnaldoTvExecutor(
    emotes: Emotes,
    client: GabrielaImageServerClient
) : GabrielaImageServerSingleCommandBase(
    emotes,
    client,
    "/api/v1/images/ednaldo-tv",
    "ednaldo_tv.png"
) {
    companion object : CommandExecutorDeclaration(EdnaldoTvExecutor::class) {
        override val options = SingleImageOptions
    }
}