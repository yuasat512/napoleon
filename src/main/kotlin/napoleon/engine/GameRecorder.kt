package napoleon.engine

import napoleon.core.Bid
import napoleon.core.GameRules.KITTY_SIZE
import napoleon.core.GameRules.PLAYER_COUNT
import napoleon.record.GameRecord
import napoleon.record.GameRecordWriter
import java.nio.file.Path
import kotlin.io.path.Path

// エンジン進行に並行して GameRecord を蓄積し、ゲーム終了時に log/napoleon.txt へ追記する。
// runRegression の replayConsistency はこの出力を固定フィクスチャと完全一致比較するため、
// ここを変更すると保存済みベースラインが無効化される。
class GameRecorder(
    private val engine: GameEngine,
    private val logFile: Path = Path("log", "napoleon.txt"),
) {
    private val writer = GameRecordWriter()
    private var record: GameRecord? = null
    private var currentTrick: GameRecord.TrickRecord? = null

    // トリック開始時の絵札保有数を記憶し、終了時に勝者の honorsGained を差分で求める。
    private var prevHonors = IntArray(PLAYER_COUNT)

    fun onGameBegin() {
        record =
            GameRecord().apply {
                bidFirstPlayerId = engine.curPlayer.id
                pointsBefore = IntArray(PLAYER_COUNT) { engine.players[it].points }
            }
        currentTrick = null
    }

    fun onBid(bid: Bid?) {
        record?.bidSequence?.add(bid)
    }

    fun onAppoint() {
        record?.apply {
            bid = engine.bid!!
            adjutantCard = engine.adjutantCard!!
            napoleonId = engine.napoleonId
            adjutantId = engine.adjutantId
        }
    }

    fun onKittyDraw() {
        record?.drawnCards = Array(KITTY_SIZE) { engine.kitty.cards[it] }
    }

    fun onKittySwap(discardIndices: IntArray) {
        record?.discardedCards = Array(KITTY_SIZE) { engine.curPlayer.hand[discardIndices[it]] }
    }

    fun onPlay(handIndex: Int) {
        val pid = engine.curPlayer.id
        val card = engine.curPlayer.hand[handIndex]
        val trick = currentTrick ?: GameRecord.TrickRecord().also { currentTrick = it }
        trick.cards[pid] = card
        if (engine.trickTurn == 0) trick.leadId = pid
    }

    fun onTrickBegin() {
        currentTrick?.apply {
            mightyInTrick = engine.mightyInTrick
            jokerDeclaredSuit = engine.jokerDeclaredSuit
        }
        prevHonors = IntArray(PLAYER_COUNT) { engine.players[it].honorsTaken }
    }

    fun onTrickEnd() {
        currentTrick?.apply {
            winnerId = engine.curPlayer.id
            winnerHonors = engine.curPlayer.honorsTaken
            honorsGained = engine.curPlayer.honorsTaken - prevHonors[winnerId]
        }
        record?.tricks?.add(currentTrick!!)
        currentTrick = null
    }

    fun onGameEnd() {
        val rec = record!!
        rec.remainingCount = engine.players[0].handCount
        rec.pointsAfter = IntArray(PLAYER_COUNT) { engine.players[it].points }
        if (rec.remainingCount > 0) {
            rec.remainingHands =
                Array(PLAYER_COUNT) { pid ->
                    val p = engine.players[pid]
                    Array(p.handCount) { p.hand[it] }
                }
        }
        writer.appendToFile(logFile, rec)
        record = null
    }
}
