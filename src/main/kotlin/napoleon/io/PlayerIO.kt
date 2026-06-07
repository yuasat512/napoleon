package napoleon.io

import napoleon.core.Bid
import napoleon.core.Card
import napoleon.core.Suit

// GameController と人間入力レイヤの境界。
// prompt* のデフォルト実装は意図的に error(...) で、auto モード (UI なし) で AI 以外の席に問い合わせが
// 走った場合にすぐ落ちるようにしてある。UI 実装が人間席用にオーバーライドする想定。
// on* は受動的な画面更新通知。onTransition は true で即時進行、false で UI が controller.step() を
// 呼ぶまで状態機械を一時停止させる。
interface PlayerIO {
    fun promptBid(): Bid? = error("No user input available")

    fun promptAdjutant(): Card = error("No user input available")

    fun promptKittySwap(): List<Int>? = error("No user input available")

    fun promptPlay(): Pair<Int, Suit?>? = error("No user input available")

    fun onDeal() {}

    fun onBid() {}

    fun onRedeal() {}

    fun onAppointPre() {}

    fun onAppoint() {}

    fun onDraw() {}

    fun onSwap() {}

    fun onPlay() {}

    fun onTrick() {}

    fun onDeclare() {}

    fun onResult() {}

    fun onEnd(finished: Boolean) {}

    // true → コントローラを即時進行させる。false → 一時停止し、UI から controller.step() で再開させる。
    // userInputNext は次フェーズで自席 (人間) の入力を待つかどうかのヒント。
    fun onTransition(
        wait: Int,
        userInputNext: Boolean,
    ): Boolean = true
}
