package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.engine.view.AiContext

// 視点プレイヤーが既に見ているカード集合 = 他家の裏向き手札・裏向きキティに「あり得ない」カード。
// 自分の手札 / 現トリックの既出札 / 公開キティの絵札 / 過去トリックの全プレイ /
// (ナポレオン視点でのみ見える) キティ捨て札 を合算する。全カードからこれを引いた残りが未確定札。
fun AiContext.seenCards(): Set<Card> {
    val seen = HashSet<Card>()
    val me = curPlayer
    for (i in 0 until me.handCount) seen += me.hand[i]
    for (p in publicPlayers) {
        if (p.handCount < me.handCount) seen += p.playedCard
    }
    for (h in kittyHonorCards) seen += h
    for (rec in trickHistory) for (play in rec.plays) seen += play.card
    napoleonKittyDiscards?.let { for (c in it) seen += c }
    return seen
}
