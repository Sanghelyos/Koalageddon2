package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.io.httpClient
import acidicoala.koalageddon.core.logging.AppLogger
import acidicoala.koalageddon.core.model.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.nio.file.Files
import java.text.StringCharacterIterator
import kotlin.io.path.writeBytes

class DownloadAndCacheKoalaTool(override val di: DI) : DIAware {
    @Serializable
    data class GitHubAsset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url")
        val browserDownloadUrl: String
    )

    @Serializable
    data class GitHubRelease(
        @SerialName("tag_name")
        val tagName: String,
        val assets: List<GitHubAsset>,
    ) {
        @Transient
        val version = SemanticVersion.fromVersion(tagName)
    }

    private val paths: AppPaths by instance()
    private val logger: AppLogger by instance()

    suspend operator fun invoke(tool: KoalaTool) = channelFlow<ILangString> {
        send(LangString("%0" to tool.name) { fetchingToolInfo })

        val releases = httpClient.get(tool.gitHubReleaseUrl).body<List<GitHubRelease>>()

        // TODO: Settings to toggle pre-release releases
        val release = releases
            .sortedByDescending { it.version }
            .find { it.version?.major == tool.majorVersion }
            ?: throw Exception("Failed to find latest supported ${tool.name} release in GitHub")

        val asset = release.assets.first()

        val assetPath = paths.getCachePath(asset.name)

        if (Files.exists(assetPath) && asset.size == withContext(Dispatchers.IO) { Files.size(assetPath) }) {
            val version = release.version?.versionString
            logger.debug("Latest supported ${tool.name} version $version is already cached")
            return@channelFlow
        }

        val assetBytes = httpClient.get(asset.browserDownloadUrl) {
            timeout {
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            }
            onDownload { downloadedBytes, contentLength ->
                if (contentLength == 0L) {
                    return@onDownload
                }

                send(
                    LangString(
                        "%0" to tool.name,
                        "%1" to downloadedBytes.toHumanReadableString(),
                        "%2" to contentLength.toHumanReadableString()
                    ) {
                        downloadingRelease
                    }
                )
            }
        }.body<ByteArray>()

        logger.debug("Finished downloading ${asset.name}")

        assetPath.writeBytes(assetBytes)

        logger.debug("Saved ${asset.name} to $assetPath")
    }

    // Source: https://stackoverflow.com/a/3758880
    private fun Long.toHumanReadableString(): String {
        var remainingBytes = this
        if (-1000 < remainingBytes && remainingBytes < 1000) {
            return "$remainingBytes B"
        }
        val ci = StringCharacterIterator("kMGTPE")
        while (remainingBytes <= -999950 || remainingBytes >= 999950) {
            remainingBytes /= 1000
            ci.next()
        }
        return String.format("%.1f %cB", remainingBytes / 1000.0, ci.current())
    }
}