package acidicoala.koalageddon.core.use_case

import acidicoala.koalageddon.core.logging.AppLogger
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import java.awt.Desktop
import java.nio.file.Path

class OpenDirectoryInExplorer(override val di: DI) : DIAware {
    private val logger: AppLogger by instance()

    operator fun invoke(path: Path) {
        logger.debug("Opening directory in explorer: $path")

        Desktop.getDesktop().open(path.toFile())
    }
}