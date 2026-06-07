package napoleon.ai.heuristic.support

import napoleon.core.Card
import napoleon.engine.view.AiContext

// 視点プレイヤーが既に見ているカード集合 = 他家の裏向き手札・裏向きキティに「あり得ない」カード。
// 自分の手札 / 現トリックの既出札 / 公開キティの絵札 / 過去トリックの全プレイ /
// (ナポレオン視点でのみ見える) キティ捨て札 を合算する。全カードからこれを引いた残りが未確定札。
fun AiContext.seenCards(): Set<Card> =
    buildSet {
        val me = curPlayer
        addAll(me.hand)
        for (p in publicPlayers) {
            if (p.handCount < me.hand.size) add(p.playedCard)
        }
        addAll(kittyHonorCards)
        for (rec in trickHistory) for (play in rec.plays) add(play.card)
        napoleonKittyDiscards?.let { addAll(it) }
    }
