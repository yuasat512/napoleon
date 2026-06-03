package napoleon.ui

import java.awt.Color
import java.io.File

// カード画像 (1_DeckM/2_DeckS) と suit アルファ (3_Suit)、アプリアイコン (4_Icon) を生成する。

private const val SUIT_W = 18
private const val SUIT_H = 20
private const val GAP_W = -2
private const val GAP_H = 4
private const val MARGIN_W = 19
private const val MARGIN_H = 14
private const val CARD_W = SUIT_W * 3 + GAP_W * 2 + MARGIN_W * 2
private const val CARD_H = SUIT_H * 4 + GAP_H * 3 + MARGIN_H * 2

private const val NUM_W = 16
private const val NUM_H = 20
private const val NUM_MARGIN_W = 2
private const val NUM_MARGIN_H = 8
private const val S_SUIT_W = 14
private const val S_SUIT_H = 16
private const val S_MARGIN_W = 3
private const val S_MARGIN_H = NUM_H + NUM_MARGIN_H + 4
private const val JOKER_MARGIN_W = 5
private const val JOKER_MARGIN_H = 8
private const val SUIT_S = FONT_SIZE
private const val LV = 4

// BGR 順 (低位バイトが青) の整数を TYPE_INT_RGB (低位バイトが青、上位がR) に並べ替える。
private fun bgrToRgb(c: Int): Int = ((c and 0xff) shl 16) or (c and 0xff00) or ((c shr 16) and 0xff)

// ♣♦♥♠★ の描画色 (TYPE_INT_RGB)。
private val COL = intArrayOf(0x000000, 0xff0000, 0xff0000, 0x000000, 0x606060)

// 3_Suit.png の各セル背景色 (♣♦♥♠★)。値は BGR で書いておき bgrToRgb で TYPE_INT_RGB に直す。
private val SUIT_PNG_COLORS =
    intArrayOf(0x00A000, 0x0080ff, 0xff60ff, 0x800000, 0x00ffff)
        .map(::bgrToRgb)
        .toIntArray()

private const val IDS_SUIT = "♣♦♥♠★"
private const val IDS_NUM = "2345678910JQKA"

// J/Q/K 共通の絵札 PNG (各 1 枚, CARD_W x CARD_H)。インデックスは ♣♦♥♠ の順。
private val FACE = listOf("face_clubs.png", "face_diamonds.png", "face_hearts.png", "face_spades.png")

// ジョーカーの絵柄 PNG (CARD_W x CARD_H)。大デッキ裏面と小デッキ joker 列で使用。
private val JOKER = listOf("joker_a.png", "joker_b.png")

// 裏面パターンの線色。CardBackPattern.generate が都度 CARD_W x CARD_H の画像を生成する。
private val BACK_COLORS = listOf(Color(240, 206, 211), Color(180, 220, 190))

class DeckGenerator(
    private val sourceDir: File = File("src/test/resources/deck-source"),
    private val outputDir: File = File("src/main/resources"),
) {
    fun generate() {
        outputDir.mkdirs()

        val mdc = Array(7) { MemDC() }
        createSuit(mdc[1], SUIT_W, SUIT_H)
        createSuit(mdc[2], S_SUIT_W, S_SUIT_H)
        createNum(mdc[3])
        createJoker(mdc[4])
        createAce(mdc[5])
        createBack(mdc[6])

        createDeckM(mdc)
        mdc[0].saveImage(File(outputDir, "1_DeckM.png"))
        createDeckS(mdc)
        mdc[0].saveImage(File(outputDir, "2_DeckS.png"))

        // 文字列描画用スートのアルファマップを RGBA PNG として保存。テキスト中で隣接する全角文字より
        // 一回り大きく見えないよう、セルは SUIT_S x SUIT_S 維持で内側を 2px 縮める。
        createSuit(mdc[0], SUIT_S, SUIT_S, SUIT_S - 2, SUIT_S - 2)
        mdc[1].setBitmap(SUIT_S * 5 shl LV, SUIT_S shl LV)
        mdc[1].copyRect(0, 0, SUIT_S * 5 shl LV, SUIT_S shl LV, mdc[0], 0, 0)
        mdc[1].setAlpha()
        mdc[1].saveAlphaPng(File(outputDir, "3_Suit.png"), SUIT_PNG_COLORS)

        // アプリアイコン (♠ + † のみ、Napoleon 文字なし) 256x256 ARGB PNG。
        val iconMdc = MemDC()
        createIcon(iconMdc)
        iconMdc.saveIconPng(File(outputDir, "4_Icon.png"), 256)

        println("Deck generation complete: ${outputDir.absolutePath}")
    }

    // 5 種のスート文字 ♣♦♥♠★ を innerW x innerH に収まる最大フォントで描画し、cellW x cellH の
    // セル内に配置する (innerW < cellW なら周囲に余白が生まれる)。次いで左右対称化 + 上半分=正立 /
    // 下半分=反転 のアルファ素材を作る。innerW/innerH 省略時はセル全体に詰めて描画する。
    private fun createSuit(
        mdc: MemDC,
        cellW0: Int,
        cellH0: Int,
        innerW0: Int = cellW0,
        innerH0: Int = cellH0,
    ) {
        val szWork = 500
        val tw = cellW0 shl LV
        val th = cellH0 shl LV
        val iw = innerW0 shl LV
        val ih = innerH0 shl LV
        mdc.setBitmap(tw * 5, th * 2)

        val mono = MemDC()
        val work = MemDC()
        mono.setBitmap(szWork, szWork)
        work.setBitmap(szWork, szWork)

        val buf = IDS_SUIT
        for (i in 0 until 5) {
            var font = szWork
            while (true) {
                mono.setFont(font, "ＭＳ Ｐゴシック")
                mono.drawRect(0, 0, szWork, szWork, 0)
                mono.textOut(20, 0, buf[i].toString())

                work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
                val rc = work.getBorder()
                // スート絵柄は閉区間幅から 1px 引いた値で評価する (createNum の +1 と異なる)。
                // 1px 詰めることで隣接全角文字との並びでスートが一回り大きく見えないようにする。
                val w = rc.right - rc.left - 1
                val h = rc.bottom - rc.top - 1
                if (w <= iw && h <= ih) {
                    val yOffset = (th - h) / 2
                    mdc.bitBlt(i * tw + (tw - w) / 2, yOffset, w, h, work, rc.left, rc.top)
                    mdc.copyRect(i * tw + tw / 2, 0, tw / 2, th, i * tw, 0, MemDC.CopyFlag.CF_X)
                    mdc.copyRect(i * tw, th, tw, th, i * tw, 0, MemDC.CopyFlag.CF_R)
                    break
                }
                font--
                if (font <= 0) error("createSuit: failed to fit suit '${buf[i]}' into $iw x $ih (cell $tw x $th)")
            }
        }
        mdc.setAlpha()
    }

    // 13 ランクの数字を NUM_W x NUM_H 内に描画。i==8 (10) は '1' を左寄せ '0' を右寄せで別々に焼く。
    // それ以外の 12 字種は全てが szW x szH に収まる最大フォントサイズを 1 回求めて使い回し、
    // 各セル中央に配置する (10 もこの単一フォントサイズを共有)。
    private fun createNum(mdc: MemDC) {
        val szWork = 600
        val szW = NUM_W shl LV
        val szH = NUM_H shl LV
        val fontName = "HGP創英ﾌﾟﾚｾﾞﾝｽEB"
        val weight = 600

        mdc.setBitmap(szW * 13, szH * 2)
        val work = MemDC()
        work.setBitmap(szWork, szWork)
        val mono = MemDC()
        val buf = IDS_NUM
        val singleIdx = (0 until 13).filter { it != 8 }

        // 全字種が szW x szH に収まる最大フォントを線形探索 (createSuit と同パターン)。
        var fontSize = szWork
        outer@ while (fontSize > 0) {
            for (i in singleIdx) {
                mono.setBitmap(szWork, szWork)
                mono.setFont(fontSize, fontName, 0, weight)
                val ci = if (i < 9) i else i + 1
                mono.textOut(100, 0, buf[ci].toString())
                work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
                val rc = work.getBorder()
                val w = rc.right - rc.left + 1
                val h = rc.bottom - rc.top + 1
                if (w > szW || h > szH) {
                    fontSize--
                    continue@outer
                }
            }
            break
        }
        if (fontSize <= 0) error("createNum: no font size fits all glyphs into $szW x $szH")

        for (i in singleIdx) {
            mono.setBitmap(szWork, szWork)
            mono.setFont(fontSize, fontName, 0, weight)
            val ci = if (i < 9) i else i + 1
            mono.textOut(100, 0, buf[ci].toString())

            work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
            val rc = work.getBorder()
            val w = rc.right - rc.left + 1
            val h = rc.bottom - rc.top + 1
            mdc.bitBlt(i * szW + (szW - w) / 2, (szH - h) / 2, w, h, work, rc.left, rc.top)
            mdc.copyRect(i * szW, szH, szW, szH, i * szW, 0, MemDC.CopyFlag.CF_R)
        }

        // i==8 ("10"): '1' を左寄せ、'0' を右寄せで別々に焼く。フォントサイズは singleIdx と共通。
        // 両字に同じ lfWidth を当てて同率に圧縮し、合計幅が szW を超えない最大値まで探索する。
        val cellX = 8 * szW

        fun bbox(
            digit: String,
            lfw: Int,
        ): IntArray {
            mono.setBitmap(szWork, szWork)
            mono.setFont(fontSize, fontName, lfw, weight)
            mono.textOut(100, 0, digit)
            work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
            val rc = work.getBorder()
            return intArrayOf(rc.left, rc.top, rc.right - rc.left + 1, rc.bottom - rc.top + 1)
        }

        // probe で線形逆算 → 念のため 1 ずつ落として szW 内に収まる最大 lfWidth を確定。
        val probe = 200
        val w1probe = bbox("1", probe)[2]
        val w0probe = bbox("0", probe)[2]
        var lfWidth10 = (probe.toDouble() * szW / (w1probe + w0probe)).toInt()
        while (lfWidth10 > 0) {
            val w1 = bbox("1", lfWidth10)[2]
            val w0 = bbox("0", lfWidth10)[2]
            if (w1 + w0 <= szW) break
            lfWidth10--
        }
        if (lfWidth10 <= 0) error("createNum: failed to fit '10' into $szW")

        // '1' を左寄せ
        val b1 = bbox("1", lfWidth10)
        mdc.bitBlt(cellX, (szH - b1[3]) / 2, b1[2], b1[3], work, b1[0], b1[1])
        // '0' を右寄せ
        val b0 = bbox("0", lfWidth10)
        mdc.bitBlt(cellX + szW - b0[2], (szH - b0[3]) / 2, b0[2], b0[3], work, b0[0], b0[1])

        mdc.copyRect(cellX, szH, szW, szH, cellX, 0, MemDC.CopyFlag.CF_R)

        mdc.setAlpha()
    }

    // "JOKER" を縦に積み上げて小カード (高さ CARD_SM_H、上下余白 4px) にちょうど収まる最大フォントを
    // 線形探索する。strip 寸法は実 glyph 寸法から自動算出 (各文字 X 中央揃え, 文字間 1px)。
    // 下半分は 180° 回転コピー (カード裏向き視点用)。
    private fun createJoker(mdc: MemDC) {
        val szWork = 500
        val gap = 24
        val unit = 1 shl LV // setAlpha が割り切れるよう strip 寸法はこの倍数に丸める
        val expectedSzH = (CARD_SM_H - 4 * 2) shl LV // 小カード上下余白(4)を除いた高さに揃える

        fun roundUp(n: Int): Int = (n + unit - 1) / unit * unit

        // 各文字を別 MemDC に描画し bbox を測る (合計サイズ確定後にまとめて貼り付ける)。
        fun renderCells(fontSize: Int): List<Pair<MemDC, MemDC.Border>> =
            "JOKER".map { c ->
                val mono = MemDC()
                mono.setBitmap(szWork, szWork)
                mono.setFont(fontSize, "ＭＳ Ｐゴシック")
                mono.textOut(150, 0, c.toString())
                mono to mono.getBorder()
            }

        fun computeSzH(cells: List<Pair<MemDC, MemDC.Border>>): Int = roundUp(cells.sumOf { (_, rc) -> rc.bottom - rc.top + 1 } + gap * 4)

        // expectedSzH 以下に収まる最大 fontSize を線形探索 (createSuit/createNum と同パターン)。
        var fontSize = szWork
        var cells = renderCells(fontSize)
        while (computeSzH(cells) > expectedSzH) {
            fontSize--
            if (fontSize <= 0) error("createJoker: no font size fits 'JOKER' into $expectedSzH")
            cells = renderCells(fontSize)
        }
        val szH = computeSzH(cells)
        check(szH == expectedSzH) { "createJoker: szH = $szH, expected = $expectedSzH (fontSize=$fontSize)" }

        val widths = cells.map { (_, rc) -> rc.right - rc.left + 1 }
        val heights = cells.map { (_, rc) -> rc.bottom - rc.top + 1 }
        val szW = roundUp(widths.max())

        mdc.setBitmap(szW, szH * 2)
        var hh = 0
        for (i in 0 until 5) {
            val (src, rc) = cells[i]
            mdc.bitBlt((szW - widths[i]) / 2, hh, widths[i], heights[i], src, rc.left, rc.top)
            hh += heights[i] + gap
        }
        mdc.copyRect(0, szH, szW, szH, 0, 0, MemDC.CopyFlag.CF_R)
        mdc.setAlpha()
    }

    // ♠ + † (XOR 重ね) + Napoleon 文字列を合成したエース絵札 (♠A 中央)。
    private fun createAce(mdc: MemDC) {
        // szH (= 1472) と同じフォントサイズだと Malgun Gothic ♠ の glyph が szH をはみ出す。
        // 実測で szH に収まる 940 を使う。
        val szFont = 940
        val szWork = 1800
        val szW = (SUIT_W * 3) shl LV
        val szH = (SUIT_H * 4 + GAP_H * 3) shl LV

        mdc.setBitmap(szW, szH)
        val work = MemDC()
        work.setBitmap(szWork, szWork)
        val buf = IDS_SUIT

        // スート (♠)
        val mono = MemDC()
        mono.setBitmap(szWork, szWork)
        mono.setFont(szFont, "Malgun Gothic")
        mono.textOut(20, 0, buf[3].toString())

        work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
        var rc = work.getBorder()
        var w = rc.right - rc.left + 1
        var h = rc.bottom - rc.top + 1
        mdc.bitBlt((szW - w) / 2, (szH - h) / 2, w, h, work, rc.left, rc.top)

        // ダガー (†) を XOR で重ね描き
        mono.setBitmap(szWork, szWork)
        mono.setFont(559, "HGP明朝B")
        mono.textOut(20, 0, "†")

        work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
        rc = work.getBorder()
        w = rc.right - rc.left + 1
        h = rc.bottom - rc.top + 1
        mdc.bitBltInvert((szW - w) / 2, (szH - h) / 2 + 16 * 5, w, h, work, rc.left, rc.top)
        mdc.copyRect(szW / 2, 0, szW / 2, szH, 0, 0, MemDC.CopyFlag.CF_X)

        // "Napoleon" 文字列を上下に配置
        mono.setBitmap(szWork, szWork)
        mono.setFont(202, "HGP創英ﾌﾟﾚｾﾞﾝｽEB")
        mono.textOut(20, 0, "Napoleon")

        work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
        rc = work.getBorder()
        w = rc.right - rc.left + 1
        h = rc.bottom - rc.top + 1
        mdc.bitBlt((szW - w) / 2, 16 * 4, w, h, work, rc.left, rc.top)
        mdc.copyRect(0, szH - (16 * 4 + h), szW, 16 * 4 + h, 0, 0, MemDC.CopyFlag.CF_R)
        mdc.setAlpha()
    }

    // アプリアイコン: createAce から Napoleon 文字列を抜いた ♠ + † のみ版。フォント・サイズは createAce と一致。
    private fun createIcon(mdc: MemDC) {
        val szFont = 940
        val szWork = 1800
        val szW = (SUIT_W * 3) shl LV
        val szH = (SUIT_H * 4 + GAP_H * 3) shl LV

        mdc.setBitmap(szW, szH)
        val work = MemDC()
        work.setBitmap(szWork, szWork)
        val buf = IDS_SUIT

        // スート (♠)
        val mono = MemDC()
        mono.setBitmap(szWork, szWork)
        mono.setFont(szFont, "Malgun Gothic")
        mono.textOut(20, 0, buf[3].toString())

        work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
        var rc = work.getBorder()
        var w = rc.right - rc.left + 1
        var h = rc.bottom - rc.top + 1
        mdc.bitBlt((szW - w) / 2, (szH - h) / 2, w, h, work, rc.left, rc.top)

        // ダガー (†) を XOR で重ね描き
        mono.setBitmap(szWork, szWork)
        mono.setFont(559, "HGP明朝B")
        mono.textOut(20, 0, "†")

        work.bitBlt(0, 0, szWork, szWork, mono, 0, 0)
        rc = work.getBorder()
        w = rc.right - rc.left + 1
        h = rc.bottom - rc.top + 1
        mdc.bitBltInvert((szW - w) / 2, (szH - h) / 2 + 16 * 5, w, h, work, rc.left, rc.top)

        // 左右対称化 (createAce と同じ)
        mdc.copyRect(szW / 2, 0, szW / 2, szH, 0, 0, MemDC.CopyFlag.CF_X)
    }

    // 大デッキ「裏 / 14 列目」用の素材を 4 列まとめて 1 枚に焼く。
    // 列 0,1 (♣,♦ 行向け) = joker_a/b.png をそのまま中央配置。
    // 列 2,3 (♥,♠ 行向け) = CardBackPattern が生成するひし形格子パターン。
    private fun createBack(mdc: MemDC) {
        mdc.setBitmap(CARD_W * 4, CARD_H)
        mdc.drawRect(0, 0, mdc.width, mdc.height, 0xffffff)

        val tmp = MemDC()

        for (j in 0 until 2) {
            tmp.loadImage(File(sourceDir, JOKER[j]))
            if (tmp.width < CARD_W || tmp.height < CARD_H) {
                error("createBack: ${JOKER[j]} is ${tmp.width}x${tmp.height}, smaller than $CARD_W x $CARD_H")
            }
            mdc.bitBlt(CARD_W * j, 0, CARD_W, CARD_H, tmp, (tmp.width - CARD_W) / 2, (tmp.height - CARD_H) / 2)
        }

        for (j in 0 until 2) {
            tmp.loadImage(CardBackPattern.generate(BACK_COLORS[j]))
            if (tmp.width < CARD_W || tmp.height < CARD_H) {
                error("createBack: back pattern $j is ${tmp.width}x${tmp.height}, smaller than $CARD_W x $CARD_H")
            }
            mdc.bitBlt(CARD_W * (j + 2), 0, CARD_W, CARD_H, tmp, 0, 0)
        }
    }

    // 大デッキ画像 1_DeckM.png を組み立てる (4 スート × 14 列 = 2-10, J, Q, K, A, 裏)。
    private fun createDeckM(mdcArr: Array<MemDC>) {
        val vc0 = 0
        val vc1 = SUIT_H
        val pxL = 0
        val pxC = (SUIT_W + GAP_W) * 1
        val pxR = (SUIT_W + GAP_W) * 2
        val pyTT = 0
        val pyTB = (SUIT_H + GAP_H) * 1
        val pyBT = (SUIT_H + GAP_H) * 2
        val pyBB = (SUIT_H + GAP_H) * 3
        val pyCT = (SUIT_H + GAP_H) * 1 / 2
        val pyCC = (SUIT_H + GAP_H) * 3 / 2
        val pyCB = (SUIT_H + GAP_H) * 5 / 2
        val pyTC = (SUIT_H + GAP_H + 1) * 3 / 4
        val pyBC = (SUIT_H + GAP_H) * 9 / 4

        val mdc = mdcArr[0]
        val suitM = mdcArr[1]
        val suitS = mdcArr[2]
        val num = mdcArr[3]
        val joker = mdcArr[4]
        val ace = mdcArr[5]
        val back = mdcArr[6]
        val jokerAlpha = joker.alpha ?: error("joker alpha not initialized")
        val jokerW = jokerAlpha.width
        val jokerH = jokerAlpha.height / 2
        mdc.setBitmap(CARD_W * 14, CARD_H * 4)
        mdc.drawRect(0, 0, mdc.width, mdc.height, 0xffffff)

        for (i in 0 until 4) {
            var bx = MARGIN_W - CARD_W
            val by = MARGIN_H + CARD_H * i

            // bxBase 内の (xx, yy) 位置に suitM (alpha) の i 列・yy 段目を color で着色して打つ。
            val color = COL[i]

            fun set(
                bxBase: Int,
                xx: Int,
                yy: Int,
                v: Int,
            ) = mdc.copyAlpha(bxBase + xx, by + yy, SUIT_W, SUIT_H, suitM, SUIT_W * i, v, color)

            // 2
            bx += CARD_W
            set(bx, pxC, pyTT, vc0)
            set(bx, pxC, pyBB, vc1)

            // 3
            bx += CARD_W
            set(bx, pxC, pyTT, vc0)
            set(bx, pxC, pyCC, vc0)
            set(bx, pxC, pyBB, vc1)

            // 4
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyBB, vc1)

            // 5
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxC, pyCC, vc0)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyBB, vc1)

            // 6
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyCC, vc0)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyCC, vc0)
            set(bx, pxR, pyBB, vc1)

            // 7
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyCC, vc0)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxC, pyTC, vc0)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyCC, vc0)
            set(bx, pxR, pyBB, vc1)

            // 8
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyCC, vc0)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxC, pyTC, vc0)
            set(bx, pxC, pyBC, vc1)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyCC, vc0)
            set(bx, pxR, pyBB, vc1)

            // 9
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyTB, vc0)
            set(bx, pxL, pyBT, vc1)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxC, pyCC, vc0)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyTB, vc0)
            set(bx, pxR, pyBT, vc1)
            set(bx, pxR, pyBB, vc1)

            // 10
            bx += CARD_W
            set(bx, pxL, pyTT, vc0)
            set(bx, pxL, pyTB, vc0)
            set(bx, pxL, pyBT, vc1)
            set(bx, pxL, pyBB, vc1)
            set(bx, pxC, pyCT, vc0)
            set(bx, pxC, pyCB, vc1)
            set(bx, pxR, pyTT, vc0)
            set(bx, pxR, pyTB, vc0)
            set(bx, pxR, pyBT, vc1)
            set(bx, pxR, pyBB, vc1)

            // J, Q, K
            val bmp = MemDC()
            bmp.loadImage(File(sourceDir, FACE[i]))
            if (bmp.width < CARD_W || bmp.height < CARD_H) {
                error("createDeckM: ${FACE[i]} is ${bmp.width}x${bmp.height}, smaller than $CARD_W x $CARD_H")
            }
            for (j in 0 until 3) {
                bx += CARD_W
                mdc.bitBlt(bx - MARGIN_W, by - MARGIN_H, CARD_W, CARD_H, bmp, (bmp.width - CARD_W) / 2, (bmp.height - CARD_H) / 2)
            }

            // A
            bx += CARD_W
            if (i == 3) {
                mdc.copyAlpha(bx + GAP_W, by, SUIT_W * 3, SUIT_H * 4 + GAP_H * 3, ace, 0, 0, COL[i])
            } else {
                set(bx, pxC, pyCC, vc0)
            }

            // 裏
            bx += CARD_W
            mdc.bitBlt(bx - MARGIN_W, by - MARGIN_H, CARD_W, CARD_H, back, CARD_W * i, 0)

            // 左上/右下のスート(S) と 数字 (j==13 のジョーカーは i<2 のみ)。
            for (j in 0 until 14) {
                if (j < 13) {
                    var bx2 = S_MARGIN_W + j * CARD_W
                    var by2 = S_MARGIN_H + i * CARD_H
                    mdc.copyAlpha(bx2, by2, S_SUIT_W, S_SUIT_H, suitS, S_SUIT_W * i, 0, COL[i])
                    mdc.copyAlpha(
                        bx2 + CARD_W - S_MARGIN_W * 2 - S_SUIT_W,
                        by2 + CARD_H - S_MARGIN_H * 2 - S_SUIT_H,
                        S_SUIT_W,
                        S_SUIT_H,
                        suitS,
                        S_SUIT_W * i,
                        S_SUIT_H,
                        COL[i],
                    )
                    bx2 = NUM_MARGIN_W + j * CARD_W
                    by2 = NUM_MARGIN_H + i * CARD_H
                    mdc.copyAlpha(bx2, by2, NUM_W, NUM_H, num, NUM_W * j, 0, COL[i])
                    mdc.copyAlpha(
                        bx2 + CARD_W - NUM_MARGIN_W * 2 - NUM_W,
                        by2 + CARD_H - NUM_MARGIN_H * 2 - NUM_H,
                        NUM_W,
                        NUM_H,
                        num,
                        NUM_W * j,
                        NUM_H,
                        COL[i],
                    )
                } else if (i < 2) {
                    val bx2 = JOKER_MARGIN_W + j * CARD_W
                    val by2 = JOKER_MARGIN_H + i * CARD_H
                    mdc.copyAlpha(bx2, by2, jokerW, jokerH, joker, 0, 0, 0)
                    mdc.copyAlpha(
                        bx2 + CARD_W - JOKER_MARGIN_W * 2 - jokerW,
                        by2 + CARD_H - JOKER_MARGIN_H * 2 - jokerH,
                        jokerW,
                        jokerH,
                        joker,
                        0,
                        jokerH,
                        0,
                    )
                }
            }
        }

        // 枠線: 4 隅に透過色 (0x00ff00) を打って丸みを表現する。
        val waku = 0x808080
        val trans = 0x00ff00
        for (i in 0 until 4) {
            for (j in 0 until 14) {
                mdc.drawRect(CARD_W * j, CARD_H * i, CARD_W, 1, waku)
                mdc.drawRect(CARD_W * j, CARD_H * i, 1, CARD_H, waku)
                mdc.drawRect(CARD_W * j, CARD_H * i + CARD_H - 1, CARD_W, 1, waku)
                mdc.drawRect(CARD_W * j + CARD_W - 1, CARD_H * i, 1, CARD_H, waku)

                mdc.drawRect(CARD_W * j, CARD_H * i + 0, 3, 1, trans)
                mdc.drawRect(CARD_W * j, CARD_H * i + 1, 2, 1, trans)
                mdc.drawRect(CARD_W * j + 2, CARD_H * i + 1, 1, 1, waku)
                mdc.drawRect(CARD_W * j, CARD_H * i + 2, 1, 1, trans)
                mdc.drawRect(CARD_W * j + 1, CARD_H * i + 2, 1, 1, waku)

                mdc.drawRect(CARD_W * j, CARD_H * i + CARD_H - 1, 3, 1, trans)
                mdc.drawRect(CARD_W * j, CARD_H * i + CARD_H - 2, 2, 1, trans)
                mdc.drawRect(CARD_W * j + 2, CARD_H * i + CARD_H - 2, 1, 1, waku)
                mdc.drawRect(CARD_W * j, CARD_H * i + CARD_H - 3, 1, 1, trans)
                mdc.drawRect(CARD_W * j + 1, CARD_H * i + CARD_H - 3, 1, 1, waku)

                mdc.drawRect(CARD_W * j + CARD_W - 3, CARD_H * i + 0, 3, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 2, CARD_H * i + 1, 2, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 3, CARD_H * i + 1, 1, 1, waku)
                mdc.drawRect(CARD_W * j + CARD_W - 1, CARD_H * i + 2, 1, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 2, CARD_H * i + 2, 1, 1, waku)

                mdc.drawRect(CARD_W * j + CARD_W - 3, CARD_H * i + CARD_H - 1, 3, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 2, CARD_H * i + CARD_H - 2, 2, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 3, CARD_H * i + CARD_H - 2, 1, 1, waku)
                mdc.drawRect(CARD_W * j + CARD_W - 1, CARD_H * i + CARD_H - 3, 1, 1, trans)
                mdc.drawRect(CARD_W * j + CARD_W - 2, CARD_H * i + CARD_H - 3, 1, 1, waku)
            }
        }
    }

    // 小デッキ画像 2_DeckS.png を生成。J/Q/K/裏 を大寸で描いてから半分に縮小し、その後 ace と
    // joker の小寸版、各カード隅の suit(S) と数字を重ね描く (2-10 の中央 pip は描かない)。
    private fun createDeckS(mdcArr: Array<MemDC>) {
        val clipnHs = 4
        val clipsHs = clipnHs + NUM_H + 4
        val cw2 = CARD_W / 2
        val ch2 = CARD_H / 2

        val mdc = mdcArr[0]
        val suitS = mdcArr[2]
        val num = mdcArr[3]
        val joker = mdcArr[4]
        val ace = mdcArr[5]
        val back = mdcArr[6]
        val jokerAlpha = joker.alpha ?: error("joker alpha not initialized")
        val jokerW = jokerAlpha.width
        val jokerH = jokerAlpha.height / 2
        mdc.setBitmap(CARD_W * 14, CARD_H * 4)
        mdc.drawRect(0, 0, mdc.width, mdc.height, 0xffffff)

        // 絵札描画 (J/Q/K + 裏(2-3 のみ))
        for (i in 0 until 4) {
            var bx = MARGIN_W + CARD_W * 8
            val by = MARGIN_H + CARD_H * i

            val bmp = MemDC()
            bmp.loadImage(File(sourceDir, FACE[i]))
            if (bmp.width < CARD_W || bmp.height < CARD_H) {
                error("createDeckS: ${FACE[i]} is ${bmp.width}x${bmp.height}, smaller than $CARD_W x $CARD_H")
            }
            for (j in 0 until 3) {
                bx += CARD_W
                mdc.bitBlt(bx - MARGIN_W, by - MARGIN_H, CARD_W, CARD_H, bmp, (bmp.width - CARD_W) / 2, (bmp.height - CARD_H) / 2)
            }

            // 裏
            bx += CARD_W * 2
            if (i > 1) {
                mdc.bitBlt(bx - MARGIN_W, by - MARGIN_H, CARD_W, CARD_H, back, CARD_W * i, 0)
            }
        }
        mdc.reducedImage(2)

        // ace は ♠ 行の A 列 (列 12)。setAlpha(5) で alpha map を半分 (level 4 → 5) に粗くしてから
        // 半サイズで打つ。joker は ♣/♦ 行の裏列 (列 13) に元 PNG を 1/2 縮小して貼る。
        ace.setAlpha(5)
        mdc.copyAlpha(
            (MARGIN_W + GAP_W + CARD_W * 12) / 2,
            (MARGIN_H + CARD_H * 3) / 2,
            SUIT_W * 3 / 2,
            (SUIT_H * 4 + GAP_H * 3) / 2,
            ace,
            0,
            0,
            COL[4],
        )
        for (j in 0 until 2) {
            val tmp = MemDC()
            tmp.loadImage(File(sourceDir, JOKER[j]))
            tmp.reducedImage(2)
            if (tmp.width < CARD_SM_W || tmp.height < CARD_SM_H) {
                error("createDeckS: ${JOKER[j]} reduced to ${tmp.width}x${tmp.height}, smaller than $CARD_SM_W x $CARD_SM_H")
            }
            mdc.bitBlt(cw2 * 13, ch2 * j, CARD_SM_W, CARD_SM_H, tmp, (tmp.width - CARD_SM_W) / 2, (tmp.height - CARD_SM_H) / 2)
        }

        // 左上/右下のスート(S) + 数字 (j==13 のジョーカーは i<2 のみ)。
        for (i in 0 until 4) {
            for (j in 0 until 14) {
                if (j < 13) {
                    var bx = S_MARGIN_W + j * cw2
                    var by = clipsHs + i * ch2
                    mdc.copyAlpha(bx, by, S_SUIT_W, S_SUIT_H, suitS, S_SUIT_W * i, 0, COL[i])
                    mdc.copyAlpha(
                        bx + cw2 - S_MARGIN_W * 2 - S_SUIT_W,
                        by + ch2 - clipsHs * 2 - S_SUIT_H,
                        S_SUIT_W,
                        S_SUIT_H,
                        suitS,
                        S_SUIT_W * i,
                        S_SUIT_H,
                        COL[i],
                    )
                    bx = NUM_MARGIN_W + j * cw2
                    by = clipnHs + i * ch2
                    mdc.copyAlpha(bx, by, NUM_W, NUM_H, num, NUM_W * j, 0, COL[i])
                    mdc.copyAlpha(
                        bx + cw2 - NUM_MARGIN_W * 2 - NUM_W,
                        by + ch2 - clipnHs * 2 - NUM_H,
                        NUM_W,
                        NUM_H,
                        num,
                        NUM_W * j,
                        NUM_H,
                        COL[i],
                    )
                } else if (i < 2) {
                    val bx = NUM_MARGIN_W + j * cw2
                    val by = clipnHs + i * ch2
                    mdc.copyAlpha(bx, by, jokerW, jokerH, joker, 0, 0, 0)
                    mdc.copyAlpha(
                        bx + cw2 - NUM_MARGIN_W * 2 - jokerW,
                        by,
                        jokerW,
                        jokerH,
                        joker,
                        0,
                        jokerH,
                        0,
                    )
                }
            }
        }

        // 枠線 (DeckM と同様、4 隅に透過色を打って丸みを表現)。
        val waku = 0x808080
        val trans = 0x00ff00
        for (i in 0 until 4) {
            for (j in 0 until 14) {
                mdc.drawRect(cw2 * j, ch2 * i, cw2, 1, waku)
                mdc.drawRect(cw2 * j, ch2 * i, 1, ch2, waku)
                mdc.drawRect(cw2 * j, ch2 * i + ch2 - 1, cw2, 1, waku)
                mdc.drawRect(cw2 * j + cw2 - 1, ch2 * i, 1, ch2, waku)

                mdc.drawRect(cw2 * j, ch2 * i + 0, 2, 1, trans)
                mdc.drawRect(cw2 * j, ch2 * i + 1, 1, 1, trans)
                mdc.drawRect(cw2 * j + 1, ch2 * i + 1, 1, 1, waku)

                mdc.drawRect(cw2 * j, ch2 * i + ch2 - 1, 2, 1, trans)
                mdc.drawRect(cw2 * j, ch2 * i + ch2 - 2, 1, 1, trans)
                mdc.drawRect(cw2 * j + 1, ch2 * i + ch2 - 2, 1, 1, waku)

                mdc.drawRect(cw2 * j + cw2 - 2, ch2 * i + 0, 2, 1, trans)
                mdc.drawRect(cw2 * j + cw2 - 1, ch2 * i + 1, 1, 1, trans)
                mdc.drawRect(cw2 * j + cw2 - 2, ch2 * i + 1, 1, 1, waku)

                mdc.drawRect(cw2 * j + cw2 - 2, ch2 * i + ch2 - 1, 2, 1, trans)
                mdc.drawRect(cw2 * j + cw2 - 1, ch2 * i + ch2 - 2, 1, 1, trans)
                mdc.drawRect(cw2 * j + cw2 - 2, ch2 * i + ch2 - 2, 1, 1, waku)
            }
        }
    }
}
