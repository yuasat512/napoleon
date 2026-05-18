package napoleon.app

import com.formdev.flatlaf.FlatLightLaf
import napoleon.config.Config
import napoleon.config.RecordStore
import napoleon.ui.GameFrame
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.LocalDateTime
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.io.path.Path
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { thread, e ->
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val entry =
            buildString {
                appendLine("[${LocalDateTime.now()}] thread=${thread.name}")
                appendLine(sw)
            }
        val message =
            runCatching {
                Path("error.txt").writeText(entry, options = arrayOf(CREATE, APPEND))
            }.fold(
                onSuccess = { "エラーが発生しました。\nアプリを再起動してください。" },
                onFailure = { "エラーが発生しました。\nログの書き込みにも失敗しました。\nアプリを再起動してください。" },
            )
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, message, "エラー", JOptionPane.ERROR_MESSAGE)
        }
    }

    FlatLightLaf.setup()

    val config = Config().load()
    if ("--debug" in args) {
        config.debug = true
        config.showAllCards = true
    }

    val recordStore = RecordStore().load()
    SwingUtilities.invokeLater { GameFrame(config, recordStore) }
}
