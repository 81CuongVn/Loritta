package net.perfectdreams.loritta.platform.discord.entities

import com.github.kevinsawicki.http.HttpRequest
import com.mrpowergamerbr.loritta.LorittaLauncher
import com.mrpowergamerbr.loritta.events.LorittaMessageEvent
import com.mrpowergamerbr.loritta.userdata.MongoServerConfig
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.extensions.await
import com.mrpowergamerbr.loritta.utils.extensions.isEmote
import com.mrpowergamerbr.loritta.utils.extensions.localized
import com.mrpowergamerbr.loritta.utils.extensions.sendMessageAsync
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import com.mrpowergamerbr.loritta.utils.locale.LegacyBaseLocale
import com.mrpowergamerbr.temmiewebhook.DiscordMessage
import com.mrpowergamerbr.temmiewebhook.TemmieWebhook
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.exceptions.PermissionException
import net.perfectdreams.loritta.api.commands.LorittaCommand
import net.perfectdreams.loritta.api.commands.LorittaCommandContext
import net.perfectdreams.loritta.api.entities.MessageChannel
import net.perfectdreams.loritta.platform.discord.commands.LorittaDiscordCommand
import net.perfectdreams.loritta.platform.discord.entities.jda.JDAGuild
import net.perfectdreams.loritta.platform.discord.entities.jda.JDAUser
import org.jsoup.Jsoup
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class DiscordCommandContext(val config: MongoServerConfig, var lorittaUser: LorittaUser, locale: BaseLocale, legacyLocale: LegacyBaseLocale, var event: LorittaMessageEvent, command: LorittaCommand, args: Array<String>, val displayArgs: Array<String>, val strippedArgs: Array<String>) : LorittaCommandContext(locale, legacyLocale, command, args) {
	var metadata = HashMap<String, Any>()

	val isPrivateChannel: Boolean
		get() = event.isFromType(ChannelType.PRIVATE)

	override val message: net.perfectdreams.loritta.platform.discord.entities.DiscordMessage
		get() = DiscordMessage(event.message)

	val discordMessage: Message
		get() = event.message

	val handle: Member
		get() {
			if (lorittaUser is GuildLorittaUser) {
				return (lorittaUser as GuildLorittaUser).member
			}
			throw RuntimeException("Trying to use getHandle() in LorittaUser!")
		}

	val userHandle: User
		get() = lorittaUser.user

	val asMention: String
		get() = lorittaUser.asMention

	override val guild: DiscordGuild?
		get() = if (event.guild != null)
			JDAGuild(event.guild!!)
		else
			null

	override val channel: MessageChannel = DiscordMessageChannel(event.channel)

	val discordGuild = event.guild

	/**
	 * Verifica se o usu??rio tem permiss??o para utilizar um comando
	 */
	fun canUseCommand(): Boolean {
		return lorittaUser.canUseCommand(this)
	}

	override fun getAsMention(addSpace: Boolean): String {
		return lorittaUser.getAsMention(addSpace) /* if (cmd is AbstractCommand) {
			val cmdOptions = lorittaUser.config.getCommandOptionsFor(cmd as AbstractCommand)
			if (cmdOptions.override) {
				if (cmdOptions.mentionOnCommandOutput)
					lorittaUser.user.asMention + (if (addSpace) " " else "")
				else
					""
			} else lorittaUser.getAsMention(true)
		} else {
			lorittaUser.getAsMention(true)
		} */
	}

	override suspend fun reply(message: String, prefix: String?, forceMention: Boolean): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		var send = ""
		if (prefix != null) {
			send = "$prefix **|** "
		}
		send = send + (if (forceMention) userHandle.asMention + " " else getAsMention(true)) + message
		return sendMessage(send)
	}

	override suspend fun reply(vararg loriReplies: LoriReply): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return reply(false, *loriReplies)
	}

	override suspend fun reply(mentionUserBeforeReplies: Boolean, vararg loriReplies: LoriReply): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		val message = StringBuilder()
		if (mentionUserBeforeReplies && config.mentionOnCommandOutput) {
			message.append(LoriReply().build(this))
			message.append("\n")
		}
		for (loriReply in loriReplies) {
			message.append(loriReply.build(this))
			message.append("\n")
		}
		return sendMessage(message.toString())
	}

	override suspend fun reply(image: BufferedImage, fileName: String, vararg loriReplies: LoriReply): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		val message = StringBuilder()
		for (loriReply in loriReplies) {
			message.append(loriReply.build(this) + "\n")
		}
		return sendFile(image, fileName, message.toString()) as net.perfectdreams.loritta.platform.discord.entities.DiscordMessage
	}

	override suspend fun sendMessage(message: String): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendMessage(MessageBuilder().append(if (message.isEmpty()) " " else message).build())
	}

	suspend fun sendMessage(message: String, embed: MessageEmbed): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendMessage(MessageBuilder().setEmbed(embed).append(if (message.isEmpty()) " " else message).build())
	}

	suspend fun sendMessage(embed: MessageEmbed): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendMessage(MessageBuilder().append(getAsMention(true)).setEmbed(embed).build())
	}

	suspend fun sendMessage(message: Message): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		var privateReply = lorittaUser.config.commandOutputInPrivate
		/* if (cmd is AbstractCommand) {
			val cmdOptions = lorittaUser.config.getCommandOptionsFor(cmd as AbstractCommand)
			if (cmdOptions.override && cmdOptions.commandOutputInPrivate) {
				privateReply = cmdOptions.commandOutputInPrivate
			}
		} */
		if (privateReply) {
			val privateChannel = lorittaUser.user.openPrivateChannel().await()
			return DiscordMessage(privateChannel.sendMessageAsync(message))
		} else {
			if (isPrivateChannel || event.textChannel!!.canTalk()) {
				val sentMessage = event.channel.sendMessage(message).await()
				if (config.deleteMessagesAfter != null)
					sentMessage.delete().queueAfter(config.deleteMessagesAfter!!, TimeUnit.SECONDS)
				return DiscordMessage(sentMessage)
			} else {
				LorittaUtils.warnOwnerNoPermission(discordGuild, event.textChannel, lorittaUser.config)
				throw RuntimeException("Sem permiss??o para enviar uma mensagem!")
			}
		}
	}

	suspend fun sendMessage(webhook: TemmieWebhook?, message: DiscordMessage) {
		if (!isPrivateChannel && webhook != null) { // Se a webhook ?? diferente de null, ent??o use a nossa webhook dispon??vel!
			webhook.sendMessage(message)
		} else { // Se n??o, iremos usar embeds mesmo...
			val builder = EmbedBuilder()
			builder.setAuthor(message.username, null, message.avatarUrl)
			builder.setDescription(message.content)
			builder.setFooter("N??o consigo usar as permiss??es de webhook aqui... ent??o estou usando o modo de pobre!", null)

			for (embed in message.embeds) {
				builder.setImage(if (embed.image != null) embed.image.url else null)
				if (embed.title != null) {
					builder.setTitle(builder.descriptionBuilder.toString() + "\n\n**" + embed.title + "**")
				}
				if (embed.description != null) {
					builder.setDescription(builder.descriptionBuilder.toString() + "\n\n" + embed.description)
				}
				if (embed.thumbnail != null) {
					builder.setThumbnail(embed.thumbnail.url)
				}
			}
			sendMessage(builder.build())
		}
	}

	override suspend fun sendFile(file: File, name: String, message: String): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendFile(file, name, message, null)
	}

	suspend fun sendFile(file: File, name: String, message: String, embed: MessageEmbed? = null): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		// Corrigir erro ao construir uma mensagem vazia
		val builder = MessageBuilder()
		builder.append(if (message.isEmpty()) " " else message)
		if (embed != null)
			builder.setEmbed(embed)
		return sendFile(file, name, builder.build())
	}

	suspend fun sendFile(file: File, name: String, message: Message): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		val inputStream = file.inputStream()
		return sendFile(inputStream, name, message)
	}

	suspend fun sendFile(image: BufferedImage, name: String, embed: MessageEmbed): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendFile(image, name, "", embed)
	}

	suspend fun sendFile(image: BufferedImage, name: String, message: String, embed: MessageEmbed? = null): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		val builder = MessageBuilder()
		builder.append(if (message.isEmpty()) " " else message)
		if (embed != null)
			builder.setEmbed(embed)

		return sendFile(image, name, builder.build())
	}

	suspend fun sendFile(image: BufferedImage, name: String, message: Message): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		// https://stackoverflow.com/a/12253091/7271796
		val output = object : ByteArrayOutputStream() {
			@Synchronized
			override fun toByteArray(): ByteArray {
				return this.buf
			}
		}

		ImageIO.write(image, "png", output)

		val inputStream = ByteArrayInputStream(output.toByteArray(), 0, output.size())

		return sendFile(inputStream, name, message)
	}

	override suspend fun sendFile(inputStream: InputStream, name: String, message: String): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		// Corrigir erro ao construir uma mensagem vazia
		val builder = MessageBuilder()
		builder.append(if (message.isEmpty()) " " else message)
		return sendFile(inputStream, name, builder.build())
	}

	suspend fun sendFile(inputStream: InputStream, name: String, embed: MessageEmbed): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		return sendFile(inputStream, name, "", embed)
	}

	suspend fun sendFile(inputStream: InputStream, name: String, message: String, embed: MessageEmbed? = null): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		// Corrigir erro ao construir uma mensagem vazia
		val builder = MessageBuilder()
		builder.append(if (message.isEmpty()) " " else message)
		if (embed != null)
			builder.setEmbed(embed)
		return sendFile(inputStream, name, builder.build())
	}

	suspend fun sendFile(inputStream: InputStream, name: String, message: Message): net.perfectdreams.loritta.platform.discord.entities.DiscordMessage {
		var privateReply = lorittaUser.config.commandOutputInPrivate
		/* if (cmd is AbstractCommand) {
        val cmdOptions = lorittaUser.config.getCommandOptionsFor(cmd as AbstractCommand)
        if (cmdOptions.override && cmdOptions.commandOutputInPrivate) {
            privateReply = cmdOptions.commandOutputInPrivate
        }
    } */
		if (privateReply) {
			val privateChannel = lorittaUser.user.openPrivateChannel().await()
			val sentMessage = privateChannel.sendMessageAsync(message)

			return DiscordMessage(sentMessage)
		} else {
			if (isPrivateChannel || event.textChannel!!.canTalk()) {
				val sentMessage = event.channel.sendMessage(message).addFile(inputStream, name).await()

				if (config.deleteMessagesAfter != null)
					sentMessage.delete().queueAfter(config.deleteMessagesAfter!!, TimeUnit.SECONDS)
				return DiscordMessage(sentMessage)
			} else {
				LorittaUtils.warnOwnerNoPermission(discordGuild, event.textChannel, lorittaUser.config)
				throw RuntimeException("Sem permiss??o para enviar uma mensagem!")
			}
		}
	}

	/**
	 * Sends an embed explaining what the command does
	 *
	 * @param context the context of the command
	 */
	override suspend fun explain() {
		val conf = config
		val ev = event

		if (conf.explainOnCommandRun) {
			val rawArguments = discordMessage.contentRaw.split(" ")
			var commandLabel = rawArguments[0]
			if (rawArguments.getOrNull(1) != null && (rawArguments[0] == "<@${loritta.discordConfig.discord.clientId}>" || rawArguments[0] == "<@!${loritta.discordConfig.discord.clientId}>")) {
				// Caso o usu??rio tenha usado "@Loritta comando", pegue o segundo argumento (no caso o "comando") em vez do primeiro (que ?? a mention da Lori)
				commandLabel = rawArguments[1]
			}
			commandLabel = commandLabel.toLowerCase()

			val embed = EmbedBuilder()
			embed.setColor(Color(0, 193, 223))
			embed.setTitle("\uD83E\uDD14 `$commandLabel`")

			val commandArguments = command.getUsage(locale)
			val usage = when {
				commandArguments.arguments.isNotEmpty() -> " `${commandArguments.build(locale)}`"
				else -> ""
			}

			var cmdInfo = command.getDescription(locale) + "\n\n"

			cmdInfo += "\uD83D\uDC81 **" + legacyLocale["HOW_TO_USE"] + ":** " + commandLabel + usage + "\n"

			for (argument in commandArguments.arguments) {
				if (argument.explanation != null) {
					cmdInfo += "${Constants.LEFT_PADDING} `${argument.build(locale)}` - "
					if (argument.defaultValue != null) {
						cmdInfo += "(Padr??o: ${argument.defaultValue}) "
					}
					cmdInfo += "${argument.explanation}\n"
				}
			}

			cmdInfo += "\n"

			// Criar uma lista de exemplos
			val examples = ArrayList<String>()
			for (example in command.getExamples(locale)) { // Adicionar todos os exemplos simples
				examples.add(commandLabel + if (example.isEmpty()) "" else " `$example`")
			}

			if (examples.isEmpty()) {
				embed.addField(
						"\uD83D\uDCD6 " + legacyLocale["EXAMPLE"],
						commandLabel,
						false
				)
			} else {
				var exampleList = ""
				for (example in examples) {
					exampleList += example + "\n"
				}
				embed.addField(
						"\uD83D\uDCD6 " + legacyLocale["EXAMPLE"] + (if (command.getExamples(locale).size == 1) "" else "s"),
						exampleList,
						false
				)
			}

			if (command is LorittaDiscordCommand && (command.botPermissions.isNotEmpty() || command.discordPermissions.isNotEmpty())) {
				var field = ""
				if (command.discordPermissions.isNotEmpty()) {
					field += "\uD83D\uDC81 Voc?? precisa ter permiss??o para ${command.discordPermissions.joinToString(", ", transform = { "`${it.localized(locale)}`" })} para utilizar este comando!\n"
				}
				if (command.botPermissions.isNotEmpty()) {
					field += "<:loritta:331179879582269451> Eu preciso de permiss??o para ${command.botPermissions.joinToString(", ", transform = { "`${it.localized(locale)}`" })} para poder executar este comando!\n"
				}
				embed.addField(
						"\uD83D\uDCDB Permiss??es",
						field,
						false
				)
			}

			val aliases = mutableSetOf<String>()
			aliases.addAll(command.labels)

			val onlyUnusedAliases = aliases.filter { it != commandLabel.replaceFirst(config.commandPrefix, "") }
			if (onlyUnusedAliases.isNotEmpty()) {
				embed.addField(
						"\uD83D\uDD00 ${legacyLocale["CommandAliases"]}",
						onlyUnusedAliases.joinToString(", ", transform = { "`" + config.commandPrefix + it + "`" }),
						true
				)
			}

			embed.setDescription(cmdInfo)
			embed.setAuthor("${userHandle.name}#${userHandle.discriminator}", null, ev.author.effectiveAvatarUrl)
			embed.setFooter(legacyLocale[command.category.fancyTitle], "${loritta.instanceConfig.loritta.website.url}assets/img/loritta_gabizinha_v1.png") // Mostrar categoria do comando
			embed.setTimestamp(Instant.now())

			if (conf.explainInPrivate) {
				ev.author.openPrivateChannel().queue {
					it.sendMessage(embed.build()).queue()
				}
			} else {
				val message = (sendMessage(getAsMention(true), embed.build()) as net.perfectdreams.loritta.platform.discord.entities.DiscordMessage).handle
				message.addReaction("???").queue()
				message.onReactionAddByAuthor(this) {
					if (it.reactionEmote.isEmote("???")) {
						message.delete().queue()
						explainArguments()
					}
				}
			}
		}
	}

	/**
	 * Sends an embed explaining how the argument works
	 *
	 * @param context the context of the command
	 */
	suspend fun explainArguments() {
		val embed = EmbedBuilder()
		embed.setColor(Color(0, 193, 223))
		embed.setTitle("\uD83E\uDD14 Como os argumentos funcionam?")
		embed.addField(
				"Estilos de Argumentos",
				"""
					`<argumento>` - Argumento obrigat??rio
					`[argumento]` - Argumento opcional
				""".trimIndent(),
				false
		)

		embed.addField(
				"Tipos de Argumentos",
				"""
					`texto` - Um texto qualquer
					`usu??rio` - Men????o, nome de um usu??rio ou ID de um usu??rio
					`imagem` - URL da imagem,  men????o, nome de um usu??rio, ID de um usu??rio e, caso nada tenha sido encontrado, ser?? pego a primeira imagem encontrada nas ??ltimas 25 mensagens.
				""".trimIndent(),
				false
		)

		val message = (sendMessage(getAsMention(true), embed.build()) as net.perfectdreams.loritta.platform.discord.entities.DiscordMessage).handle
		message.addReaction("???").queue()
		message.onReactionAddByAuthor(this) {
			if (it.reactionEmote.isEmote("???")) {
				message.delete().queue()
				explain()
			}
		}
	}

	/**
	 * Gets an user from the argument index via mentions, username#oldDiscriminator, effective name, username and user ID
	 *
	 * @param argument the argument index on the rawArgs array
	 * @return         the user object or null, if nothing was found
	 * @see            User
	 */
	override suspend fun getUserAt(argument: Int): User? {
		if (this.args.size > argument) { // Primeiro iremos verificar se existe uma imagem no argumento especificado
			val link = this.args[argument] // Ok, ser?? que isto ?? uma URL?

			// Vamos verificar por men????es, uma men????o do Discord ?? + ou - assim: <@123170274651668480>
			for (user in this.discordMessage.mentionedUsers) {
				if (user.asMention == link.replace("!", "")) { // O replace ?? necess??rio j?? que usu??rios com nick tem ! no mention (?)
					// Diferente de null? Ent??o vamos usar o avatar do usu??rio!
					return user
				}
			}

			// Vamos tentar procurar pelo username + discriminator
			if (this.guild is DiscordGuild) {
				val handle = (this.guild as JDAGuild).handle
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val split = link.split("#").dropLastWhile { it.isEmpty() }.toTypedArray()

					if (split.size == 2 && split[0].isNotEmpty()) {
						val matchedMember = handle.getMembersByName(split[0], false).stream().filter { it -> it.user.discriminator == split[1] }.findFirst()

						if (matchedMember.isPresent) {
							return matchedMember.get().user
						}
					}
				}

				// Ok ent??o... se n??o ?? link e nem men????o... Que tal ent??o verificar por nome?
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val matchedMembers = handle.getMembersByEffectiveName(link, true)

					if (!matchedMembers.isEmpty()) {
						return matchedMembers[0].user
					}
				}

				// Se n??o, vamos procurar s?? pelo username mesmo
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val matchedMembers = handle.getMembersByName(link, true)

					if (!matchedMembers.isEmpty()) {
						return matchedMembers[0].user
					}
				}
			}

			// Ok, ent??o s?? pode ser um ID do Discord!
			try {
				val user = LorittaLauncher.loritta.lorittaShards.retrieveUserById(link)

				if (user != null) { // Pelo visto ??!
					return user
				}
			} catch (e: Exception) {
			}
		}
		return null
	}

	/**
	 * Gets an user from the argument index via mentions, username#oldDiscriminator, effective name, username and user ID
	 *
	 * @param argument the argument index on the rawArgs array
	 * @return         the user object or null, if nothing was found
	 * @see            User
	 */
	override suspend fun getUser(link: String?): net.perfectdreams.loritta.api.entities.User? {
		if (link != null) {
			// Vamos verificar por men????es, uma men????o do Discord ?? + ou - assim: <@123170274651668480>
			for (user in this.discordMessage.mentionedUsers) {
				if (user.asMention == link.replace("!", "")) { // O replace ?? necess??rio j?? que usu??rios com nick tem ! no mention (?)
					// Diferente de null? Ent??o vamos usar o avatar do usu??rio!
					return JDAUser(user)
				}
			}

			// Vamos tentar procurar pelo username + discriminator
			if (this.guild is DiscordGuild) {
				val handle = (this.guild as JDAGuild).handle
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val split = link.split("#").dropLastWhile { it.isEmpty() }.toTypedArray()

					if (split.size == 2 && split[0].isNotEmpty()) {
						val matchedMember = handle.getMembersByName(split[0], false).stream().filter { it -> it.user.discriminator == split[1] }.findFirst()

						if (matchedMember.isPresent) {
							return JDAUser(matchedMember.get().user)
						}
					}
				}

				// Ok ent??o... se n??o ?? link e nem men????o... Que tal ent??o verificar por nome?
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val matchedMembers = handle.getMembersByEffectiveName(link, true)

					if (!matchedMembers.isEmpty()) {
						return JDAUser(matchedMembers[0].user)
					}
				}

				// Se n??o, vamos procurar s?? pelo username mesmo
				if (!this.isPrivateChannel && !link.isEmpty()) {
					val matchedMembers = handle.getMembersByName(link, true)

					if (!matchedMembers.isEmpty()) {
						return JDAUser(matchedMembers[0].user)
					}
				}
			}

			// Ok, ent??o s?? pode ser um ID do Discord!
			try {
				val user = LorittaLauncher.loritta.lorittaShards.retrieveUserById(link)

				if (user != null) { // Pelo visto ??!
					return JDAUser(user)
				}
			} catch (e: Exception) {
			}
		}

		return null
	}

	/**
	 * Gets an image URL from the argument index via valid URLs at the specified index
	 *
	 * @param argument   the argument index on the rawArgs array
	 * @param search     how many messages will be retrieved from the past to get images (default: 25)
	 * @param avatarSize the size of retrieved user avatars from Discord (default: 2048)
	 * @return           the image URL or null, if nothing was found
	 */
	override suspend fun getImageUrlAt(argument: Int, search: Int, avatarSize: Int) = getImageUrl(this.args.getOrNull(argument), search, avatarSize)

	/**
	 * Gets an image URL from the argument index via valid URLs at the specified index
	 *
	 * @param argument   the argument index on the rawArgs array
	 * @param search     how many messages will be retrieved from the past to get images (default: 25)
	 * @param avatarSize the size of retrieved user avatars from Discord (default: 2048)
	 * @return           the image URL or null, if nothing was found
	 */
	override suspend fun getImageUrl(link: String?, search: Int, avatarSize: Int): String? {
		if (link != null) {
			if (LorittaUtils.isValidUrl(link))
				return link // Se ?? um link, vamos enviar para o usu??rio agora

			// Vamos verificar por usu??rios no argumento especificado
			val user = getUser(link) as DiscordUser?
			if (user != null)
				return user.effectiveAvatarUrl + "?size=" + avatarSize

			// Ainda n??o?!? Vamos verificar se ?? um emoji.
			// Um emoji custom do Discord ?? + ou - assim: <:loritta:324931508542504973>
			for (emote in this.discordMessage.emotes) {
				if (link.equals(emote.asMention, ignoreCase = true)) {
					return emote.imageUrl
				}
			}

			// Se n??o ?? nada... ent??o talvez seja um emoji padr??o do Discord??o!
			// Na verdade ?? um emoji padr??o...
			try {
				var unicodeEmoji = LorittaUtils.toUnicode(link.codePointAt(0)) // Vamos usar codepoints porque emojis
				unicodeEmoji = unicodeEmoji.substring(2) // Remover coisas desnecess??rias
				val toBeDownloaded = "https://twemoji.maxcdn.com/2/72x72/$unicodeEmoji.png"
				if (HttpRequest.get(toBeDownloaded).code() == 200) {
					return toBeDownloaded
				}
			} catch (e: Exception) {
			}
		}

		if (this.guild is JDAGuild) {
			val handle = (this.guild as JDAGuild).handle
			// Ainda nada v??lido? Quer saber, desisto! Vamos pesquisar as mensagens antigas deste servidor & embeds ent??o para encontrar attachments...
			if (search > 0 && !this.isPrivateChannel && handle.selfMember.hasPermission(this.event.textChannel!!, Permission.MESSAGE_HISTORY)) {
				try {
					val message = this.discordMessage.channel.history.retrievePast(search).await()

					attach@ for (msg in message) {
						for (embed in msg.embeds) {
							if (embed.image != null) {
								return embed.image!!.url
							}
						}
						for (attachment in msg.attachments) {
							if (attachment.isImage) {
								return attachment.url
							}
						}
					}
				} catch (e: PermissionException) {
				}
			}
		}

		return null
	}

	/**
	 * Gets an image from the argument index via valid URLs at the specified index
	 *
	 * @param argument   the argument index on the rawArgs array
	 * @param search     how many messages will be retrieved from the past to get images (default: 25)
	 * @param avatarSize the size of retrieved user avatars from Discord (default: 2048)
	 * @return           the image object or null, if nothing was found
	 * @see              BufferedImage
	 */
	override suspend fun getImageAt(argument: Int, search: Int, avatarSize: Int): BufferedImage? {
		var toBeDownloaded = getImageUrlAt(argument, 0, avatarSize)

		if (toBeDownloaded == null) {
			if (args.isNotEmpty()) {
				return ImageUtils.createTextAsImage(256, 256, args.joinToString(" "))
			}

			toBeDownloaded = getImageUrlAt(argument, search, avatarSize)
		}

		if (toBeDownloaded == null)
			return null

		// Vamos baixar a imagem!
		try {
			// Workaround para imagens do prnt.scr/prntscr.com (mesmo que o Lightshot seja um lixo)
			if (toBeDownloaded.contains("prnt.sc") || toBeDownloaded.contains("prntscr.com")) {
				val document = Jsoup.connect(toBeDownloaded).get()
				val elements = document.getElementsByAttributeValue("property", "og:image")
				if (!elements.isEmpty()) {
					toBeDownloaded = elements.attr("content")
				}
			}
			return LorittaUtils.downloadImage(toBeDownloaded ?: return null)
		} catch (e: Exception) {
			return null
		}
	}

	/**
	 * Gets an image from the argument index via valid URLs at the specified index
	 *
	 * @param argument   the argument index on the rawArgs array
	 * @param search     how many messages will be retrieved from the past to get images (default: 25)
	 * @param avatarSize the size of retrieved user avatars from Discord (default: 2048)
	 * @return           the image object or null, if nothing was found
	 * @see              BufferedImage
	 */
	override suspend fun getImage(text: String, search: Int, avatarSize: Int): BufferedImage? {
		var toBeDownloaded = getImageUrl(text, 0, avatarSize)

		if (toBeDownloaded == null) {
			if (args.isNotEmpty()) {
				return ImageUtils.createTextAsImage(256, 256, args.joinToString(" "))
			}

			toBeDownloaded = getImageUrl(text, search, avatarSize)
		}

		if (toBeDownloaded == null)
			return null

		// Vamos baixar a imagem!
		try {
			// Workaround para imagens do prnt.scr/prntscr.com (mesmo que o Lightshot seja um lixo)
			if (toBeDownloaded.contains("prnt.sc") || toBeDownloaded.contains("prntscr.com")) {
				val document = Jsoup.connect(toBeDownloaded).get()
				val elements = document.getElementsByAttributeValue("property", "og:image")
				if (!elements.isEmpty()) {
					toBeDownloaded = elements.attr("content")
				}
			}
			return LorittaUtils.downloadImage(toBeDownloaded ?: return null)
		} catch (e: Exception) {
			return null
		}
	}
	/**
	 *
	 * Gets a role from argument index
	 *
	 * @param argument the argument index
	 * @see Role
	 * */
	fun getRoleAt(argument: Int): Role? {
		if (this.args.size > argument) {
			val roleStr = this.args[argument]
			val guild = this.discordGuild!!
			val message = this.discordMessage

			try {
				// Tentar encontrar por cargos mencionados
				if (message.mentionedRoles[argument] != null)
					return this.discordMessage.mentionedRoles[argument]

				// Tentar encontrar por ID de cargo
				if (roleStr.isValidSnowflake() && guild.getRoleById(roleStr) != null)
					return guild.getRoleById(roleStr)

				// Tentar encontrar por nome
				if (guild.getRolesByName(roleStr, true).isNotEmpty())
					return guild.getRolesByName(roleStr, true).first()
			} catch(e: Exception) {
				// Se for algum motivo der erro, apenas retorne null
				return null
			}
		}
		return null
	}
}