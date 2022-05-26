package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.modules

import com.rabbitmq.client.Channel
import dev.kord.common.entity.DiscordRole
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.value
import dev.kord.gateway.Event
import dev.kord.gateway.GuildCreate
import dev.kord.gateway.GuildDelete
import dev.kord.gateway.GuildRoleCreate
import dev.kord.gateway.GuildRoleDelete
import dev.kord.gateway.GuildRoleUpdate
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.DiscordGatewayEventsProcessor
import net.perfectdreams.loritta.cinnamon.platform.utils.toLong
import net.perfectdreams.loritta.cinnamon.pudding.tables.cache.DiscordGuildRoles
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import pw.forst.exposed.insertOrUpdate

class DiscordCacheModule(private val m: DiscordGatewayEventsProcessor) : ProcessDiscordEventsModule(RABBITMQ_QUEUE) {
    companion object {
        const val RABBITMQ_QUEUE = "discord-cache-module"
        private val logger = KotlinLogging.logger {}
    }

    override fun setupQueueBinds(channel: Channel) {
        channel.queueBindToModuleQueue("event.guild-create")
        channel.queueBindToModuleQueue("event.guild-delete")
        channel.queueBindToModuleQueue("event.guild-role-create")
        channel.queueBindToModuleQueue("event.guild-role-update")
        channel.queueBindToModuleQueue("event.guild-role-delete")
    }

    override suspend fun processEvent(event: Event) {
        when (event) {
            is GuildCreate -> {
                logger.info { "Howdy ${event.guild.id} (${event.guild.name})! Is unavailable? ${event.guild.unavailable}" }
                createOrUpdateRoleBulk(event.guild.id, event.guild.roles)
            }
            is GuildRoleCreate -> {
                createOrUpdateRole(event.role.guildId, event.role.role)
            }
            is GuildRoleUpdate -> {
                createOrUpdateRole(event.role.guildId, event.role.role)
            }
            is GuildRoleDelete -> {
                deleteRole(event.role.guildId, event.role.id)
            }
            is GuildDelete -> {
                // If the unavailable field is not set, the user/bot was removed from the guild.
                if (event.guild.unavailable.value == null) {
                    logger.info { "Someone removed me @ ${event.guild.id}! :(" }
                    removeGuildData(event.guild.id)
                }
            }
            else -> {}
        }
    }

    private suspend fun createOrUpdateRoleBulk(guildId: Snowflake, roles: List<DiscordRole>) {
        m.services.transaction {
            for (role in roles) {
                _createOrUpdateRole(guildId, role)
            }
        }
    }

    private suspend fun createOrUpdateRole(guildId: Snowflake, role: DiscordRole) {
        m.services.transaction {
            _createOrUpdateRole(guildId, role)
        }
    }

    private fun _createOrUpdateRole(guildId: Snowflake, role: DiscordRole) = DiscordGuildRoles.insertOrUpdate(DiscordGuildRoles.guildId, DiscordGuildRoles.roleId) {
        it[DiscordGuildRoles.guildId] = guildId.toLong()
        it[DiscordGuildRoles.roleId] = role.id.toLong()
        it[DiscordGuildRoles.name] = role.name
        it[DiscordGuildRoles.color] = role.color
        it[DiscordGuildRoles.hoist] = role.hoist
        it[DiscordGuildRoles.icon] = role.icon.value
        it[DiscordGuildRoles.unicodeEmoji] = role.icon.value
        it[DiscordGuildRoles.position] = role.position
        it[DiscordGuildRoles.permissions] = role.permissions.code.value.toLong()
        it[DiscordGuildRoles.managed] = role.managed
        it[DiscordGuildRoles.mentionable] = role.mentionable
    }

    private suspend fun deleteRole(guildId: Snowflake, roleId: Snowflake) {
        m.services.transaction {
            DiscordGuildRoles.deleteWhere {
                (DiscordGuildRoles.guildId eq guildId.toLong()) and (DiscordGuildRoles.roleId eq roleId.toLong())
            }
        }
    }

    private suspend fun removeGuildData(guildId: Snowflake) {
        logger.info { "Removing $guildId's cached data..." }
        m.services.transaction {
            DiscordGuildRoles.deleteWhere {
                (DiscordGuildRoles.guildId eq guildId.toLong())
            }
        }
    }
}