
import com.typesafe.config.ConfigFactory
import kotlinx.serialization.hocon.Hocon
import kotlinx.serialization.hocon.decodeFromConfig
import net.perfectdreams.loritta.platform.kord.LorittaKord
import net.perfectdreams.loritta.platform.kord.utils.config.RootConfig
import org.junit.jupiter.api.Test

class CheckConfigTest {
    private fun loadFromJar(inputPath: String): String {
        val inputStream = LorittaKord::class.java.getResourceAsStream(inputPath)
        return inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    @Test
    fun `check config`() {
        val stream = loadFromJar("/loritta.conf")
        Hocon.decodeFromConfig<RootConfig>(ConfigFactory.parseReader(stream.reader()))
    }
}