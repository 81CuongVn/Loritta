/**
 * File containing Pudding extensions, mostly extensions that has Kord references
 */
package net.perfectdreams.loritta.cinnamon.platform.utils

import dev.kord.common.entity.Snowflake
import net.perfectdreams.discordinteraktions.common.entities.User
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import net.perfectdreams.loritta.cinnamon.pudding.services.UsersService

fun UserId(snowflake: Snowflake) = UserId(snowflake.value)
fun Snowflake.toLong() = this.value.toLong()

suspend fun UsersService.getUserProfile(user: User) = getUserProfile(UserId(user.id.value))
suspend fun UsersService.getOrCreateUserProfile(user: User) = getOrCreateUserProfile(UserId(user.id.value))

suspend fun UsersService.getUserAchievements(user: User) = getUserAchievements(UserId(user.id.value))