package net.perfectdreams.loritta

import com.mrpowergamerbr.loritta.LorittaLauncher
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.extensions.await
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.lorittaShards
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.*

class SponsorsAdvertisement(val m: QuirkyStuff, val config: QuirkyConfig) {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	var task: Job? = null

	fun start() {
		logger.info { "Starting Sponsors Advertisement Task..." }

		task = GlobalScope.launch(LorittaLauncher.loritta.coroutineDispatcher) {
			while (true) {
				val calendar = Calendar.getInstance()
				calendar[Calendar.DAY_OF_WEEK] = Calendar.WEDNESDAY
				calendar[Calendar.HOUR_OF_DAY] = 17
				calendar[Calendar.MINUTE] = 0
				calendar[Calendar.SECOND] = 0
				calendar[Calendar.MILLISECOND] = 0
				var millis = calendar.timeInMillis

				if (System.currentTimeMillis() > millis) {
					calendar.add(Calendar.DAY_OF_YEAR, 7)
				}

				millis = calendar.timeInMillis
				val wait = millis - System.currentTimeMillis()

				logger.info { "Waiting ${wait}ms until we broadcast the sponsored message! Will be triggered at $millis epoch" }
				delay(millis - System.currentTimeMillis())

				broadcastSponsoredMessage()
			}
		}
	}

	suspend fun broadcastSponsoredMessage() {
		val guild = lorittaShards.getGuildById(Constants.PORTUGUESE_SUPPORT_GUILD_ID)

		if (guild != null) {
			val textChannel = guild.getTextChannelById(config.sponsorsAdvertisement.channelId)

			if (textChannel != null) {
				if (loritta.sponsors.isNotEmpty()) {
					val message = StringBuilder()
					message.append("@everyone <:pantufa_mention:400416007664959499>\n\n")
					message.append("<:smol_gessy:593907632784408644> Mais uma Quarta-Feira, mais uma... <:smol_gessy:593907632784408644>\n\n")
					message.append("**<:lori_rica:593979718919913474> Quarta Patrocinada da <@297153970613387264>**! <:lori_rica:593979718919913474>\n\n")
					message.append("Para me manter online, ?? necess??rio ter dinheiro... E n??, dinheiro n??o cai do c??u. <:loriChateada:611371449659162624>\n\nPor isso eu tenho os meus **incr??veis patrocinadores**! Ent??o... agrade??a os patrocinadores deste m??s entrando nos servidores deles... sem eles, talvez eu nem estaria aqui hoje, te ajudando e divertindo em v??rios outros servidores! <:lori_cheese:592779169059045379>")
					textChannel.sendMessage(message.toString()).await()

					for (sponsor in loritta.sponsors) {
						textChannel.sendMessage(sponsor.link).await()
					}

					textChannel.sendMessage("*Queria ter o seu servidor aqui? Ent??o leia o <#615556330396319744>!* <:lori_feliz:519546310978830355>").await()
							.addReaction("lori_rica:593979718919913474")
							.await()
				}
			}
		}
	}
}