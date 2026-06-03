package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.core.CardStrength
import napoleon.core.GameRules.HONOR_COUNT
import napoleon.core.Rank
import napoleon.core.Suit
import napoleon.engine.view.AiContext
import napoleon.engine.view.PublicPlayerView

enum class Decisive { NONE, WIN, LOSS }

// プレイ判断のための共通評価ヘルパー。現在のリーダー判定、確実勝ち判定、
// 「このトリックを取ればゲーム決着が確定するか」の判定などを提供する。
class TrickEvaluator(
    private val context: AiContext,
    private val roleInference: RoleInference,
) {
    // 候補札でこのトリックを取った場合、ゲームが WIN/LOSS で確定するかを判定する。
    // 副官未公開のときは「副官候補集合」の中で最悪値 (ナポ軍合計の下限・連合軍合計の下限) を
    // 採用することで、誤判定 (まだ確定していないのに確定と見なす) を防ぐ。
    fun classifyDecisiveOnTake(candidateIsHonor: Boolean): Decisive {
        val n = context.bid!!.target
        val hLow = countTrickHonors() + (if (candidateIsHonor) 1 else 0)
        val napId = context.napoleonId
        val napHonors = context.publicPlayers[napId].honorsTaken
        val totalHonors = context.publicPlayers.sumOf { it.honorsTaken }
        val candidates = roleInference.adjutantCandidates()
        if (candidates.isEmpty()) return Decisive.NONE
        var tMin = Int.MAX_VALUE
        var eMin = Int.MAX_VALUE
        for (c in candidates) {
            val t = if (c == napId) napHonors else napHonors + context.publicPlayers[c].honorsTaken
            val e = totalHonors - t
            if (t < tMin) tMin = t
            if (e < eMin) eMin = e
        }
        if (tMin + hLow >= n && eMin >= 1) return Decisive.WIN
        if (eMin + hLow >= HONOR_COUNT + 1 - n) return Decisive.LOSS
        return Decisive.NONE
    }

    fun canBeatLeader(
        candidate: Card,
        leaderStrength: Int,
    ): Boolean {
        if (candidate.isMighty()) {
            // よろめき (♥Q) が既に出ていればマイティはトリックを取れない。
            val me = context.curPlayer
            for (p in context.publicPlayers) {
                if (p.id != me.id && p.handCount < me.handCount && p.playedCard.isSlip()) return false
            }
        }
        return context.strengthOf(candidate) > leaderStrength
    }

    fun determineCurrentLeader(): PublicPlayerView {
        val me = context.curPlayer
        var best: PublicPlayerView = me
        var bestStrength = Int.MIN_VALUE
        var found = false
        for (p in context.publicPlayers) {
            if (p.handCount < me.handCount) {
                val s = context.strengthOf(p.playedCard)
                if (!found || s > bestStrength) {
                    bestStrength = s
                    best = p
                    found = true
                }
            }
        }
        return best
    }

    // この札を出したときに台札となるスート。リード (trickTurn==0) ではジョーカーは宣言スート・それ以外は札のスート、
    // フォロー中は確定済みの context.leadSuit。3 つの勝ち/リーダー評価が同一の前処理を共有するためまとめる。
    private fun effectiveLeadSuit(card: Card): Suit =
        when {
            context.trickTurn == 0 -> if (card.isJoker()) chooseJokerLeadSuit() else card.suit
            else -> context.leadSuit
        }

    // 候補手札を出した「直後」の暫定リーダー。場の既出札に自分の候補札を加えて判定する。
    // determineCurrentLeader が自分の着手前の状態 (context.strengthOf = 現在のセイム/マイティ) を見るのに対し、
    // これは自分が出すことでセイム成立/不成立 (§7-5) やマイティ出現が変わり暫定トップが入れ替わる効果まで織り込む。
    // 例: セイムで最強だった味方の 2 が、台札スート不所持の絵札投入でセイムを失い既出の敵台札札に抜かれる筋を、
    // その絵札を上乗せする前に検出できる。同強度の既出札は自分より先着なので、厳密超え (>) のときだけ更新する。
    fun provisionalLeaderAfter(handIdx: Int): PublicPlayerView {
        val me = context.curPlayer
        val card = me.hand[handIdx]
        val trump = context.trump
        val leadSuit = effectiveLeadSuit(card)
        val mighty = context.mightyInTrick || card.isMighty()
        val same = sameActiveAfter(card)
        var best: PublicPlayerView = me
        var bestStrength = CardStrength.evaluate(card, trump, leadSuit, mighty, same)
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (p.handCount < me.handCount) {
                val s = CardStrength.evaluate(p.playedCard, trump, leadSuit, mighty, same)
                if (s > bestStrength) {
                    bestStrength = s
                    best = p
                }
            }
        }
        return best
    }

    fun countTrickHonors(): Int {
        val me = context.curPlayer
        var n = 0
        for (p in context.publicPlayers) {
            if (p.handCount < me.handCount && p.playedCard.rank.isHonor) n++
        }
        // 1 トリック目はキティに残った絵札も「このトリックを取れば獲得できる絵札」に含める。
        if (context.trickHistory.isEmpty()) n += context.kittyHonorCards.size
        return n
    }

    // この札を出してこのトリックを取り切れるかの判定。残った脅威カードの中に、
    // 出された (= リードに追従できる、または切り札・ジョーカーで割り込める) かつ
    // 自分より強い札が存在しなければ確実勝ち。
    fun isCertainWinAfter(handIdx: Int): Boolean {
        val me = context.curPlayer
        val card = me.hand[handIdx]
        val trump = context.trump

        if (context.trickTurn != 0) {
            val leader = determineCurrentLeader()
            if (!canBeatLeader(card, context.strengthOf(leader.playedCard))) return false
        }

        val leadSuit = effectiveLeadSuit(card)
        val mightyInTrick = context.mightyInTrick || card.isMighty()
        val sameActive = sameActiveAfter(card)

        val myStrength = CardStrength.evaluate(card, trump, leadSuit, mightyInTrick, sameActive)
        val excluded = context.seenCards()
        val leadVoidPossible = isLeadSuitVoidPossible(leadSuit)

        for (threat in Card.ALL) {
            if (threat in excluded) continue
            if (!canThreatPlayInTrick(threat, leadSuit, leadVoidPossible)) continue
            val threatMighty = mightyInTrick || threat.isMighty()
            val threatStrength = CardStrength.evaluate(threat, trump, leadSuit, threatMighty, sameActive)
            if (threatStrength > myStrength) return false
        }
        return true
    }

    // isCertainWinAfter の健全版。「どんな残り手札・着手をされてもこの札がトリックを取り切る」ことが保証される
    // 場合だけ true を返し、false positive (取れないのに true) を絶対に出さないことを最優先する。
    // isCertainWinAfter は判明済み void がなければ後続の切り札割り込みを「あり得ない」と楽観視し、さらにセイム
    // 成立も前提に強さを採るため、未判明 void による ruff やセイム崩しで実際には取れない筋を取り切ると誤判定する。
    // 本メソッドはそれらを最悪ケースとして織り込む:
    //   (1) 後続席は台札スート void であり得る (= 切り札・役札で割り込める) とみなし、観測済み void で「持ち得ない/
    //       追従できない」と確定する筋だけを脅威から除外する。
    //   (2) セイムは後続の void で崩れ得るので、自札がセイム前提で強い場合は「崩れた世界 (same=false)」でも勝てる
    //       必要があるとみなす (台札スートの 2 は same が崩れると最弱に戻るため、事実上 2 は保証勝ちにならない)。
    fun isGuaranteedWinAfter(handIdx: Int): Boolean {
        val me = context.curPlayer
        val card = me.hand[handIdx]
        val trump = context.trump

        if (context.trickTurn != 0) {
            val leader = determineCurrentLeader()
            if (!canBeatLeader(card, context.strengthOf(leader.playedCard))) return false
        }

        val leadSuit = effectiveLeadSuit(card)
        val mightyInTrick = context.mightyInTrick || card.isMighty()

        // このトリックでまだ着手していない後続席。誰もいなければ (= 自分が最終手番) リーダー超え確認済みなので取り切る。
        val remaining = context.publicPlayers.filter { it.id != me.id && it.handCount == me.handCount }
        if (remaining.isEmpty()) return true

        val excluded = context.seenCards()

        // 到達し得るセイム状態。後続が台札スート void になれば崩れる (same=false は後続がいる限り常に到達可能)。
        // 自札がセイムを維持する札 (sameActiveAfter) なら、後続が全員追従して成立する世界 (same=true) も到達可能。
        val sameStates = mutableListOf(false)
        if (sameActiveAfter(card)) sameStates += true

        for (same in sameStates) {
            val myStrength = CardStrength.evaluate(card, trump, leadSuit, mightyInTrick, same)
            // 既出札 (現トリックで自分より先に出た札) もこの same 世界で再評価する。決定時のセイム前提では自札が
            // 勝っていても、後続の void でセイムが崩れた世界では既出のリーダー札に逆転され得る (台札スートの 2 で頻出)。
            // 先着札は同強度なら相手が勝つので >= で弾く。
            for (p in context.publicPlayers) {
                if (p.handCount >= me.handCount) continue
                val played = CardStrength.evaluate(p.playedCard, trump, leadSuit, mightyInTrick, same)
                if (played >= myStrength) return false
            }
            for (threat in Card.ALL) {
                if (threat in excluded) continue
                if (!anyRemainingCanPlay(threat, remaining, leadSuit)) continue
                val threatMighty = mightyInTrick || threat.isMighty()
                val threatStrength = CardStrength.evaluate(threat, trump, leadSuit, threatMighty, same)
                if (threatStrength > myStrength) return false
            }
        }
        return true
    }

    // 後続席の誰かがこの脅威札を持ち得て、かつこのトリックで合法に出せるか (最悪ケース判定)。観測済み void で
    // 「持ち得ない」「追従できない」と確定する筋だけを除外し、判明していない限り後続席は台札スート void であり得る
    // (= 切り札等で割り込める) とみなす。台札スート以外の非切り札は取れない (NON_CONTRIBUTING) ので出せると数えても
    // 強さ比較で害はない。
    private fun anyRemainingCanPlay(
        threat: Card,
        remaining: List<PublicPlayerView>,
        leadSuit: Suit,
    ): Boolean {
        for (p in remaining) {
            if (threat.isJoker()) {
                if (!context.knownNoJoker[p.id]) return true
                continue
            }
            if (threat.suit in context.knownVoids[p.id]) continue
            return true
        }
        return false
    }

    fun canThreatPlayInTrick(
        threat: Card,
        leadSuit: Suit,
        leadVoidPossible: Boolean,
    ): Boolean {
        if (threat.isJoker()) return true
        if (leadSuit == Suit.NONE) return true
        if (threat.suit == leadSuit) return true
        return leadVoidPossible
    }

    // リードスート不所持 (= 切り札等で割り込める) プレイヤーが存在しうるかを判定する。
    // 既に void と判明している、または過去にそのスートがリードされていれば余地あり。
    fun isLeadSuitVoidPossible(leadSuit: Suit): Boolean {
        if (leadSuit == Suit.NONE) return true
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (leadSuit in context.knownVoids[p.id]) return true
        }
        for (rec in context.trickHistory) {
            if (rec.effectiveLeadSuit == leadSuit) return true
        }
        return false
    }

    // 「この card を出したトリックがセイムになり得るか」。エンジンのセイム判定 (GameEngine.sameCandidate) と
    // 同一条件にする: リード時は「2トリック目以降 (trickHistory が空でない) かつ役札でない」。セイムはスートを
    // 問わず成立する (§7-5) ため切り札を除外してはならない — 切り札の 2 リードもセイムで最強になり得る。
    fun sameActiveAfter(card: Card): Boolean =
        when {
            context.trickTurn == 0 ->
                context.trickHistory.isNotEmpty() &&
                    !card.isPowerCard(context.trump)
            else -> context.sameCandidate && card.suit == context.leadSuit
        }

    // 候補手札インデックスのうち inherentPower が最小のものを返す。
    fun weakestByPower(indices: List<Int>): Int {
        val me = context.curPlayer
        return indices.minByOrNull { inherentPower(me.hand[it]) }!!
    }

    // 捨て/温存の序列で使うランク序数。2 はセイム成立時に最強化する (§7-5) 特別な数札なので、素の序列でも
    // 数札の最上位 = 10 (RANK_T) 相当に格上げし、3〜9 より捨て後回し・温存する (切り札の 2 も同様)。実トリックの
    // 強弱は CardStrength が別途扱うので、ここは温存/捨ての優先度のみに効く。
    private fun preserveOrdinal(card: Card): Int = if (card.rank == Rank.RANK_2) Rank.RANK_T.ordinal else card.rank.ordinal

    // 「この札の素の強さ」を、文脈 (リードスートや既出役札) から切り離して採点する。
    // リード候補の温存判定など、トリックを取ったり負けたりする評価とは別軸の指標。
    fun inherentPower(card: Card): Int {
        val trump = context.trump
        return when {
            card.isMighty() -> 990
            card.isJoker() -> 980
            card.isRightBower(trump) -> 970
            card.isLeftBower(trump) -> 960
            card.suit == trump -> 500 + preserveOrdinal(card)
            card.rank.isHonor -> 100 + card.rank.ordinal
            else -> preserveOrdinal(card)
        }
    }

    fun dumpKey(card: Card): Int {
        val trump = context.trump
        return when {
            card.isHighRoleCard(trump) -> 5000 + inherentPower(card)
            card.rank.isHonor && card.suit == trump -> 4000 + card.rank.ordinal
            card.rank.isHonor -> 3000 + card.rank.ordinal
            card.suit == trump -> 2000 + preserveOrdinal(card)
            else -> 1000 + inherentPower(card)
        }
    }

    fun suitAlreadyLed(s: Suit): Boolean {
        if (s == context.trump || s == Suit.NONE) return false
        for (rec in context.trickHistory) {
            if (rec.effectiveLeadSuit == s) return true
        }
        return false
    }

    // ジョーカー先頭リード時のスート宣言を選ぶ。切り札を台札に指定して全員に切り札のマストフォローを強い、敵切り札を
    // 1 枚刈る (ジョーカーはマイティ以外には勝つので取り切れる)。切り札がスペードのときは敵マイティにジョーカーが
    // 食われ得るため、マイティが味方 (自分の手札 or 副官指定) にあるときだけスペードを指定し、なければ最も手薄なサイド
    // スートに逃がす。「ジョーカー保持かつマイティが味方にない」のは副官に JOKER を指定したナポがキティで JOKER を
    // 拾った副官なしのときだけ (AdjutantPlanner の不変条件: ナポ軍は JOKER/MIGHTY のどちらかを必ず保有) なので、この
    // 逃がしは実質その救済。
    fun chooseJokerLeadSuit(): Suit {
        val trump = context.trump
        if (trump != Suit.SPADES) return trump
        if (isMightyOnOurSide()) return trump
        return fewestNonTrumpSuit()
    }

    private fun isMightyOnOurSide(): Boolean {
        val me = context.curPlayer
        if ((0 until me.handCount).any { me.hand[it].isMighty() }) return true
        return context.adjutantCard.isMighty()
    }

    private fun fewestNonTrumpSuit(): Suit {
        val me = context.curPlayer
        val trump = context.trump
        return Suit.realEntries
            .filter { it != trump }
            .minByOrNull { s -> (0 until me.handCount).count { me.hand[it].suit == s } }
            ?: error("trump excludes every real suit — impossible")
    }
}
