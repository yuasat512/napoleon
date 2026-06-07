package napoleon.stats

// stats ハーネスのレポート整形ヘルパ。端末は等幅フォント前提で、ASCII (コード < 0x80) を 1、
// それ以外 (全角) を 2 桁として数える。

// ASCII を 1、全角を 2 と数えた、等幅フォント上の表示桁数。
fun dispWidth(s: String): Int = s.sumOf { if (it.code < 0x80) 1 else 2 }

// 表示桁数 width まで左を空白で詰める (右揃え)。全角を含むヘッダを数値列の右端に合わせるのに使う。
fun padStartDisp(
    s: String,
    width: Int,
): String {
    val pad = width - dispWidth(s)
    return if (pad > 0) " ".repeat(pad) + s else s
}

// 表示桁数 width まで右を空白で詰める (左揃え)。
fun padEndDisp(
    s: String,
    width: Int,
): String {
    val pad = width - dispWidth(s)
    return if (pad > 0) s + " ".repeat(pad) else s
}
