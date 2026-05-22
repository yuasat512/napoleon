package napoleon.core

import napoleon.core.GameRules.PLAYER_COUNT

// プレイヤーの表示用ラベル。ログ整形と UI のプレイヤー表示で共有する。
val PLAYER_NAMES: List<String> =
    listOf("a", "b", "c", "d", "e")
        .also { require(it.size == PLAYER_COUNT) { "PLAYER_NAMES size mismatch: ${it.size} vs $PLAYER_COUNT" } }
