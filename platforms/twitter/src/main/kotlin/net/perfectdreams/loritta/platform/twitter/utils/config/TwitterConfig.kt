package net.perfectdreams.loritta.platform.twitter.utils.config

import kotlinx.serialization.Serializable

@Serializable
data class TwitterConfig(
    val consumerKey: String,
    val consumerSecret: String,
    val accessToken: String,
    val accessTokenSecret: String
)