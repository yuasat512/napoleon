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

    // 既に場に出た or 自分の手札にあるなど「他者の手札にあり得ない」カード集合。
    // 残存脅威カードの列挙時に除外する。
    fun buildExcludedSet(): HashSet<Card> {
        val excluded = HashSet<Card>()
        val me = context.curPlayer
        for (i in 0 until me.handCount) excluded += me.hand[i]
        for (p in context.publicPlayers) {
            if (p.handCount < me.handCount) excluded += p.playedCard
        }
        for (h in context.kittyHonorCards) excluded += h
        for (rec in context.trickHistory) for (play in rec.plays) excluded += play.card
        if (context.jokerPlayed) excluded += Card.JOKER
        return excluded
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

        val leadSuit: Suit =
            when {
                context.trickTurn == 0 -> if (card.isJoker()) chooseJokerLeadSuit() else card.suit
                else -> context.leadSuit
            }
        val mightyInTrick = context.mightyInTrick || card.isMighty()
        val sameActive = sameActiveAfter(card)

        val myStrength = CardStrength.evaluate(card, trump, leadSuit, mightyInTrick, sameActive)
        val excluded = buildExcludedSet()
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

    fun sameActiveAfter(card: Card): Boolean =
        when {
            context.trickTurn == 0 ->
                context.trickHistory.isNotEmpty() &&
                    !card.isJoker() &&
                    card.suit != context.trump
            else -> context.sameCandidate && card.suit == context.leadSuit
        }

    // 候補手札インデックスのうち inherentPower が最小のものを返す。
    fun weakestByPower(indices: List<Int>): Int {
        val me = context.curPlayer
        return indices.minByOrNull { inherentPower(me.hand[it]) }!!
    }

    // 「この札の素の強さ」を、文脈 (リードスートや既出役札) から切り離して採点する。
    // リード候補の温存判定など、トリックを取ったり負けたりする評価とは別軸の指標。
    fun inherentPower(card: Card): Int {
        val trump = context.trump
        return when {
            card.isMighty() -> 990
            card.isJoker() -> 980
            card.isRightBower(trump) -> 970
            card.isLeftBower(trump) -> 960
            card.suit == trump -> 500 + card.rank.ordinal
            card.rank.isHonor -> 100 + card.rank.ordinal
            // 未リードのスートの 2 はセイム成立で最強になり得るため、温存価値が高い。
            card.rank == Rank.RANK_2 && !suitAlreadyLed(card.suit) -> Rank.entries.size
            else -> card.rank.ordinal
        }
    }

    fun dumpKey(card: Card): Int {
        val trump = context.trump
        return when {
            card.isHighRoleCard(trump) -> 5000 + inherentPower(card)
            card.rank.isHonor && card.suit == trump -> 4000 + card.rank.ordinal
            card.rank.isHonor -> 3000 + card.rank.ordinal
            card.suit == trump -> 2000 + card.rank.ordinal
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

    // ジョーカー先頭リード時のスート宣言を選ぶ。A を持つスートは「自分の A が無効化される」のを避け、
    // 敵の void を狙わない (敵が切り札で割り込んで取られる) ようにし、最も短いスートを宣言する。
    fun chooseJokerLeadSuit(): Suit {
        val me = context.curPlayer
        val trump = context.trump
        var bestSuit: Suit? = null
        var bestScore = Int.MAX_VALUE
        for (s in Suit.realEntries) {
            if (s == trump) continue
            var count = 0
            var hasAce = false
            for (i in 0 until me.handCount) {
                val c = me.hand[i]
                if (c.suit != s) continue
                count++
                if (c.rank == Rank.RANK_A) hasAce = true
            }
            if (hasAce) continue
            if (isAnyOpponentVoidInSuit(s)) continue
            val score = count * 10 + s.ordinal
            if (score < bestScore) {
                bestScore = score
                bestSuit = s
            }
        }
        bestSuit?.let { return it }
        var fallback = trump
        var fallbackCount = Int.MAX_VALUE
        for (s in Suit.realEntries) {
            val count = (0 until me.handCount).count { me.hand[it].suit == s }
            if (count < fallbackCount) {
                fallbackCount = count
                fallback = s
            }
        }
        return fallback
    }

    fun isAnyOpponentVoidInSuit(s: Suit): Boolean {
        val me = context.curPlayer
        for (p in context.publicPlayers) {
            if (p.id == me.id) continue
            if (roleInference.isLikelyTeammate(p.id)) continue
            if (s in context.knownVoids[p.id]) return true
        }
        return false
    }
}
