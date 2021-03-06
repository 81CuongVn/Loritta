package net.perfectdreams.loritta.commands.actions

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.LorittaLauncher
import com.mrpowergamerbr.loritta.dao.Profile
import com.mrpowergamerbr.loritta.network.Databases
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.extensions.getRandom
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.locale.Gender
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.perfectdreams.commands.annotation.Subcommand
import net.perfectdreams.loritta.api.commands.*
import net.perfectdreams.loritta.platform.discord.entities.DiscordCommandContext
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.Color
import java.io.File

abstract class ActionCommand(labels: Array<String>) : LorittaCommand(labels, CommandCategory.ACTION) {
    override val needsToUploadFiles = true

    abstract fun getResponse(locale: BaseLocale, first: User, second: User): String
    abstract fun getFolderName(): String
    abstract fun getEmoji(): String
    abstract fun getEmbedColor(): Color

    val cachedGifs by lazy {
        val list = mutableListOf<CachedGif>()

        val folder = File(Loritta.ASSETS, "actions/${getFolderName()}")
        val folderNames = listOf(
                Constants.ACTION_GENERIC,
                Constants.ACTION_MALE_AND_FEMALE,
                Constants.ACTION_FEMALE_AND_MALE,
                Constants.ACTION_MALE_AND_MALE,
                Constants.ACTION_FEMALE_AND_FEMALE,
                Constants.ACTION_BOTH
        )

        folderNames.forEach {
            val gifs = File(folder, it).listFiles().filter { it.extension == "gif" || it.extension == "png" }

            gifs.forEach { file ->
                list.add(
                        CachedGif(
                                it,
                                file.name
                        )
                )
            }
        }

        list
    }

    override fun getUsage(locale: BaseLocale): CommandArguments {
        return arguments {
            argument(ArgumentType.USER) {
                optional = false
            }
        }
    }

    override fun getExamples(locale: BaseLocale): List<String> {
        return locale.getWithType("commands.actions.examples")
    }

    private fun getGifsFor(userGender: Gender, receiverGender: Gender): List<CachedGif> {
        val folderNames = userGender.getValidActionFolderNames(receiverGender).toMutableList()
        if (folderNames.size != 1 && Loritta.RANDOM.nextBoolean()) // Remover "generic", para evitar muitas gifs repetidas
            folderNames.remove("generic")

        return cachedGifs.filter { folderNames.any { folderName -> folderName == it.folderName } }
    }

    suspend fun runAction(context: DiscordCommandContext, user: User, userProfile: Profile?, receiver: User, receiverProfile: Profile?) {
        val response: String
        val locale = context.locale
        val senderProfile = userProfile ?: LorittaLauncher.loritta.getOrCreateLorittaProfile(user.id)
        val recProfile = receiverProfile ?: LorittaLauncher.loritta.getOrCreateLorittaProfile(receiver.id)

        // Anti-gente idiota
        if (this is KissCommand && receiver.id == LorittaLauncher.loritta.discordConfig.discord.clientId) {
            context.reply(
                    LoriReply(
                            locale["commands.actions.kiss.responseAntiIdiot"],
                            "\uD83D\uDE45"
                    )
            )
            return
        }

        // R U a boy or girl?
        val userGender = transaction(Databases.loritta) { senderProfile.settings.gender }
        val receiverGender = transaction(Databases.loritta) { recProfile.settings.gender }

        response = getResponse(locale, user, receiver)

        // Quem tentar estapear a Loritta, vai ser estapeado
        var files = if ((this is SlapCommand || this is AttackCommand || this is KissCommand) && receiver.id == LorittaLauncher.loritta.discordConfig.discord.clientId) {
            getGifsFor(receiverGender, userGender)
        } else {
            getGifsFor(userGender, receiverGender)
        }

        while (files.isEmpty()) {
            // Caso n??o tenha nenhuma GIF dispon??vel, vamos abrir o nosso "leque" de GIFs, para evitar que d?? erro
            files = getGifsFor(Gender.UNKNOWN, Gender.UNKNOWN)
        }

        val randomImage = files.getRandom()

        val message = context.sendMessage(
                user.asMention,
                EmbedBuilder()
                        .setDescription("${getEmoji()} $response")
                        .setColor(getEmbedColor())
                        .setImage(loritta.instanceConfig.loritta.website.url + "assets/img/actions/${getFolderName()}/${randomImage.folderName}/${randomImage.fileName}")
                        .setFooter(locale["commands.actions.clickToRetribute", "\uD83D\uDD01"], null)
                        .build()
        ).handle

        if (user != receiver) {
            // Para evitar floods de actions, n??s apenas iremos adicionar a rea????o *caso* o usu??rio tenha usado o comando em outra pessoa
            // Se fosse na mesma, o usu??rio pode ficar clicando na rea????o para "spammar" mensagens
            message.addReaction("\uD83D\uDD01").queue()

            message.onReactionAdd(context) {
                if (it.reactionEmote.name == "\uD83D\uDD01" && it.user.id == receiver.id) {
                    message.removeAllFunctions()

                    runAction(context, receiver, recProfile, user, null)
                }
            }
        }
    }

    @Subcommand
    suspend fun root(context: DiscordCommandContext, locale: BaseLocale) {
        if (context.args.isNotEmpty()) {
            val user = context.getUserAt(0)

            if (user == null) {
                context.reply(
                        LoriReply(
                                locale["commands.userDoesNotExist"],
                                Constants.ERROR
                        )
                )
                return
            }

            runAction(context, context.userHandle, context.lorittaUser.profile, user, null)
        } else {
            context.explain()
        }
    }

    data class CachedGif(
            val folderName: String,
            val fileName: String
    )
}