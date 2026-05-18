package napoleon.core

object GameRules {
    const val PLAYER_COUNT = 5
    const val HAND_SIZE = 10
    const val HONOR_COUNT = 20
    const val KITTY_SIZE = Card.CARD_COUNT - PLAYER_COUNT * HAND_SIZE
    const val INVALID_INDEX = -1
}
