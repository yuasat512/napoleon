package napoleon.stats

import napoleon.ai.PlayerStrategy
import napoleon.ai.heuristic.HeuristicStrategy
import napoleon.ai.heuristic.log.PlayDebugLogger
import napoleon.ai.heuristic.log.PlayRoute
import napoleon.config.Config
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.engine.GameEngine
import napoleon.io.PlayerIO
import napoleon.session.GameController
import napoleon.session.ScoreKeeper

// HeuristicStrategy 同士を debug 付きで多数ゲーム走らせ、リード/フォロー手番でどの戦術ルートが何回
// 発火したかをまとめて集計する (旧 FollowBranchScan + LeadBranchScan の統合版)。
//
// PlayDebugLogger が出す reason は "ルートラベル | 可変補助" の形 (DETAIL_SEP 区切り)。本 Scan は前半の
// ルートラベルだけを集計キーにし、ラベル先頭の "リード:"/"フォロー:" でフェーズを振り分ける。ルートの
// 一覧を持たず実際に流れてきたラベルでそのまま数えるので、PlayRoute を増減しても Scan の改修は不要。
// team (ナポ軍 / 連合) は行内の role= で判別する。
// 実行: ./gradlew :runPlayScan (-Dgames= -Dseed= 可)
fun main() {
    val seed = System.getProperty("seed")?.toIntOrNull() ?: 12345
    val games = System.getProperty("games")?.toIntOrNull() ?: 10000

    val tally = PlayTally()
    captureStdoutLines({ tally.consume(it) }) {
        val config =
            Config().apply {
                gameCount = games
                auto = true
                gameLog = false
                debug = true
                this.seed = seed
            }
        val engine = GameEngine(config)
        val strategies: Array<PlayerStrategy> = Array(PLAYER_COUNT) { HeuristicStrategy(engine) }
        val scoreKeeper = ScoreKeeper(engine.players, config, null)
        val controller = GameController(engine, strategies, scoreKeeper, null, object : PlayerIO {})
        controller.start()
    }
    tally.report(seed, games)
}

private class PlayTally {
    private val roleRegex = Regex("role=(\\w+)")

    // フェーズごとに [ラベル -> [ナポ軍件数, 連合件数]]。出現順を保つ (件数で並べ替えるのは report 時)。
    private val lead = PhaseCounts()
    private val follow = PhaseCounts()
    private var forcedSingle = 0

    fun consume(line: String) {
        if (!line.startsWith("[HeuristicAI")) return
        val rIdx = line.indexOf(" reason=")
        if (rIdx < 0) return // bid/kitty 行など reason= を持たない行は対象外
        val reason = line.substring(rIdx + " reason=".length)
        // 固定ルート = DETAIL_SEP より前。可変補助 (候補札など) は集計に使わない。
        val route = reason.substringBefore(PlayDebugLogger.DETAIL_SEP)
        val isAlly = (roleRegex.find(line)?.groupValues?.get(1) ?: return) == "ALLY"

        when {
            route == PlayRoute.FORCED_SINGLE.label -> forcedSingle++
            route.startsWith(LEAD_PREFIX) -> lead.add(route, isAlly)
            route.startsWith(FOLLOW_PREFIX) -> follow.add(route, isAlly)
        }
    }

    fun report(
        seed: Int,
        games: Int,
    ) {
        println("=== Play branch scan (all HeuristicStrategy, seed=$seed, games=$games) ===")
        println("(参考) 合法手1枚で強制 : $forcedSingle  ※戦術分岐ではないため下記の判断総数に含めない")
        println()
        lead.print("リード分岐")
        println()
        follow.print("フォロー分岐")
    }

    private companion object {
        const val LEAD_PREFIX = "リード:"
        const val FOLLOW_PREFIX = "フォロー:"
    }
}

// 1 フェーズ (リード or フォロー) の集計。ラベル × team の件数表を持ち、レポート 1 ブロックを描く。
private class PhaseCounts {
    private val counts = LinkedHashMap<String, IntArray>()
    private var napTotal = 0
    private var allyTotal = 0

    fun add(
        label: String,
        isAlly: Boolean,
    ) {
        val slot = counts.getOrPut(label) { IntArray(2) }
        if (isAlly) {
            slot[1]++
            allyTotal++
        } else {
            slot[0]++
            napTotal++
        }
    }

    fun print(title: String) {
        val total = napTotal + allyTotal
        println("--- $title ---")
        println("判断総数 : $total  (ナポ軍=$napTotal 連合=$allyTotal)")
        if (counts.isEmpty()) {
            println("(発火なし)")
            return
        }
        // 数値列 (ASCII 固定幅 "%8d %8.2f%%" = 18 桁) は %format で完全に揃う。可変長で全角混じりのラベルは
        // 右端に置いて揃え崩れを防ぎ、ヘッダの全角タイトルだけ表示幅で右揃えして数値列に重ねる。
        val numCol = 18
        val sep = " | "
        println(
            padStartDisp("ナポ軍", numCol) + sep + padStartDisp("連合", numCol) + sep +
                padStartDisp("合計", numCol) + sep + "分岐",
        )
        val maxLabel = counts.keys.maxOf { dispWidth(it) }
        println("-".repeat(numCol * 3 + sep.length * 3 + maxLabel))
        counts.entries
            .sortedByDescending { it.value[0] + it.value[1] }
            .forEach { (label, c) ->
                val nap = c[0]
                val ally = c[1]
                val sum = nap + ally
                println(
                    "%8d %8.2f%% | %8d %8.2f%% | %8d %8.2f%% | %s".format(
                        nap,
                        pct(nap, napTotal),
                        ally,
                        pct(ally, allyTotal),
                        sum,
                        pct(sum, total),
                        label,
                    ),
                )
            }
        println("各 % は team 内の判断総数に対する割合 (合計列のみ全体比)。")
    }

    private fun pct(
        n: Int,
        base: Int,
    ): Double = if (base == 0) 0.0 else 100.0 * n / base

    // ASCII (コード < 0x80) を 1、それ以外 (全角) を 2 と数えた、等幅フォント上の表示桁数。
    private fun dispWidth(s: String): Int = s.sumOf { if (it.code < 0x80) 1 else 2 }

    // 表示桁数 width まで左を空白で詰める (右揃え)。全角を含むヘッダを数値列の右端に合わせるのに使う。
    private fun padStartDisp(
        s: String,
        width: Int,
    ): String {
        val pad = width - dispWidth(s)
        return if (pad > 0) " ".repeat(pad) + s else s
    }
}
