package napoleon.ui

const val CANVAS_W = 714
const val CANVAS_H = 474

const val FONT_SIZE = 20

// 吹き出し / 情報ボックスの縁とその内部テキストの間隔 (片側)。
// ボックスサイズはこの 2 倍ぶん拡張され、テキストはボックス内側へこのぶんオフセットして描画される。
const val BOX_PADDING = 4

data class Rect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)
