package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.config

import kotlinx.serialization.Serializable
import net.perfectdreams.loritta.cinnamon.common.utils.config.LorittaConfig
import net.perfectdreams.loritta.cinnamon.platform.utils.config.LorittaDiscordConfig

@Serializable
data class RootConfig(
    val eventsPerBatch: Int,
    val loritta: LorittaConfig,
    val discord: LorittaDiscordConfig,
    val pudding: PuddingConfig
)