package napoleon.engine.view

import napoleon.core.Card

interface PublicPlayerView {
    val id: Int
    val handCount: Int
    val honorsTaken: Int
    val playedCard: Card
}

interface SelfPlayerView : PublicPlayerView {
    val hand: Array<Card>
}
