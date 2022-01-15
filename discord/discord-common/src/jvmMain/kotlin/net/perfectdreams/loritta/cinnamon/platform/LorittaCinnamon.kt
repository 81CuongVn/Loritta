package net.perfectdreams.loritta.cinnamon.platform

import dev.kord.common.entity.Snowflake
import dev.kord.rest.request.KtorRequestException
import dev.kord.rest.service.RestClient
import io.ktor.client.*
import net.perfectdreams.discordinteraktions.api.entities.User
import net.perfectdreams.loritta.cinnamon.common.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.common.utils.config.LorittaConfig
import net.perfectdreams.loritta.cinnamon.platform.utils.config.DiscordInteractionsConfig
import net.perfectdreams.loritta.cinnamon.platform.utils.config.LorittaDiscordConfig
import net.perfectdreams.loritta.cinnamon.platform.utils.config.ServicesConfig
import net.perfectdreams.loritta.cinnamon.pudding.Pudding
import net.perfectdreams.loritta.cinnamon.pudding.data.CachedUserInfo
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import kotlin.random.Random

/**
 * Represents a Loritta Morenitta (Cinnamon) implementation.
 *
 * This should be extended by other modules :3
 */
abstract class LorittaCinnamon(
    val config: LorittaConfig,
    val discordConfig: LorittaDiscordConfig,
    val interactionsConfig: DiscordInteractionsConfig,
    val servicesConfig: ServicesConfig,

    val languageManager: LanguageManager,
    val services: Pudding,
    val http: HttpClient
) {
    // TODO: *Really* set a random seed
    val random = Random(0)

    val rest = RestClient(discordConfig.token)

    suspend fun getCachedUserInfo(userId: UserId): CachedUserInfo? {
        // First, try getting the cached user info from the database
        val cachedUserInfoFromDatabase = services.users.getCachedUserInfoById(userId)
        if (cachedUserInfoFromDatabase != null)
            return cachedUserInfoFromDatabase

        // If not present, get it from Discord!
        val restUser = try {
            rest.user.getUser(Snowflake(userId.value))
        } catch (e: KtorRequestException) {
            null
        }

        if (restUser != null) {
            // If the REST user really exists, then let's update it in our database and then return the cached user info
            services.users.insertOrUpdateCachedUserInfo(
                UserId(restUser.id.value),
                restUser.username,
                restUser.discriminator,
                restUser.avatar
            )

            return CachedUserInfo(
                UserId(restUser.id.value),
                restUser.username,
                restUser.discriminator,
                restUser.avatar
            )
        }

        return null
    }

    suspend fun insertOrUpdateCachedUserInfo(user: User) {
        services.users.insertOrUpdateCachedUserInfo(
            UserId(user.id.value),
            user.name,
            user.discriminator,
            user.avatar.avatarId
        )
    }
}