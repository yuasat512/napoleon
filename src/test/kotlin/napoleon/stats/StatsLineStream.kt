package napoleon.stats

import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

// debug ログ ([HeuristicAI ...] 行など) を解析する stats ハーネス用の共通ユーティリティ。
// block 実行中だけ System.out を横取りし、改行区切りの 1 行ごとに onLine を呼ぶ。巨大なログを
// メモリやファイルに残さず行ごとに数えて捨てるためのもの。block 終了後は UTF-8 の実 stdout に戻す
// ので、日本語のレポートはそのまま println で出力できる (Windows の SJIS 文字化けを回避)。
// Windows の既定 stdout は SJIS で日本語が化けるため、UTF-8 で書く実 stdout を返す。
fun utf8Stdout(): PrintStream = PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8")

fun captureStdoutLines(
    onLine: (String) -> Unit,
    block: () -> Unit,
) {
    val realOut = utf8Stdout()
    System.setOut(PrintStream(LineTallyStream(onLine), true, "UTF-8"))
    try {
        block()
    } finally {
        System.setOut(realOut)
    }
}

// 改行までバイトを溜め、UTF-8 で 1 行に復元してコールバックへ渡す OutputStream。
private class LineTallyStream(
    private val onLine: (String) -> Unit,
) : OutputStream() {
    private val buf = ByteArrayOutputStream(256)

    override fun write(b: Int) {
        when (b) {
            '\n'.code -> {
                onLine(buf.toString("UTF-8"))
                buf.reset()
            }
            '\r'.code -> {}
            else -> buf.write(b)
        }
    }
}
