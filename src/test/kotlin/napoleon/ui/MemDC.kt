package napoleon.ui

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import javax.imageio.ImageIO

// DeckGenerator 用の描画バッファ。BufferedImage を TYPE_INT_RGB (0x00RRGGBB) で持ち、
// 1bit 描画も同じバッファで FG=0xffffff/BG=0x000000 として扱う (getBorder などは「非0=前景」前提)。
class MemDC {
    enum class CopyFlag { CF_R, CF_X, CF_Y }

    class AlphaData(
        val width: Int,
        val height: Int,
        val level: Int,
        val map: ShortArray,
    )

    data class Border(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var image: BufferedImage = createBuffer(1, 1)
    private var pixels: IntArray = (image.raster.dataBuffer as DataBufferInt).data
    private var graphics: Graphics2D = newGraphics(image)
    private var currentFont: Font? = null

    var alpha: AlphaData? = null
        private set

    fun setBitmap(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        graphics.dispose()
        image = createBuffer(width, height)
        pixels = (image.raster.dataBuffer as DataBufferInt).data
        graphics = newGraphics(image)
        currentFont?.let { graphics.font = it }
        alpha = null
    }

    fun setFont(
        size: Int,
        name: String,
        lfWidth: Int = 0,
        weight: Int = 0,
    ) {
        // weight 600 以上を BOLD として扱う。
        val style = if (weight >= 600) Font.BOLD else Font.PLAIN
        val resolvedName = resolveFontFamily(name)
        var font = Font(resolvedName, style, size)
        if (lfWidth > 0) {
            // lfWidth は平均文字幅としての目標値。'M' 単独で割ると幅広文字に引きずられて digits が
            // 圧縮されるので、実描画対象 (0-9, A-Z) の平均との比でフォントを X 軸方向にスケールする。
            val tmpG = image.createGraphics()
            try {
                val widths = tmpG.getFontMetrics(font).widths
                var sum = 0L
                var count = 0
                for (c in '0'.code..'9'.code) {
                    sum += widths[c]
                    count++
                }
                for (c in 'A'.code..'Z'.code) {
                    sum += widths[c]
                    count++
                }
                val avg = (sum.toDouble() / count).coerceAtLeast(1.0)
                val ratio = lfWidth.toDouble() / avg
                font = font.deriveFont(AffineTransform.getScaleInstance(ratio, 1.0))
            } finally {
                tmpG.dispose()
            }
        }
        currentFont = font
        graphics.font = font
    }

    fun textOut(
        x: Int,
        y: Int,
        text: String,
    ) {
        graphics.color = Color.WHITE
        val fm = graphics.fontMetrics
        // (x,y) はセル左上として渡す前提なので、ベースライン基準の drawString に ascent 分ずらして渡す。
        graphics.drawString(text, x, y + fm.ascent)
    }

    // 非 0 画素の包含矩形を返す。right/bottom は閉区間 (= 該当画素の座標そのもの)。
    fun getBorder(): Border {
        val w = width
        val h = height
        var left = -1
        var right = -1
        var top = -1
        var bottom = -1

        outer@ for (x in 0 until w) {
            for (y in 0 until h) {
                if (pixels[y * w + x] != 0) {
                    left = x
                    break@outer
                }
            }
        }
        outer@ for (x in w - 1 downTo 0) {
            for (y in 0 until h) {
                if (pixels[y * w + x] != 0) {
                    right = x
                    break@outer
                }
            }
        }
        outer@ for (y in 0 until h) {
            for (x in 0 until w) {
                if (pixels[y * w + x] != 0) {
                    top = y
                    break@outer
                }
            }
        }
        outer@ for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                if (pixels[y * w + x] != 0) {
                    bottom = y
                    break@outer
                }
            }
        }

        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            error("getBorder: bitmap is entirely black")
        }
        return Border(left, top, right, bottom)
    }

    fun bitBlt(
        dx: Int,
        dy: Int,
        w: Int,
        h: Int,
        src: MemDC,
        sx: Int,
        sy: Int,
    ) {
        for (y in 0 until h) {
            val dstRow = (dy + y) * width + dx
            val srcRow = (sy + y) * src.width + sx
            System.arraycopy(src.pixels, srcRow, pixels, dstRow, w)
        }
    }

    // dst ^= src (24bit RGB XOR)。アルファチャネルは保持しない。
    fun bitBltInvert(
        dx: Int,
        dy: Int,
        w: Int,
        h: Int,
        src: MemDC,
        sx: Int,
        sy: Int,
    ) {
        for (y in 0 until h) {
            val dstRow = (dy + y) * width + dx
            val srcRow = (sy + y) * src.width + sx
            for (x in 0 until w) {
                pixels[dstRow + x] = (pixels[dstRow + x] xor src.pixels[srcRow + x]) and 0xffffff
            }
        }
    }

    fun copyRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        src: MemDC,
        srcX: Int,
        srcY: Int,
    ) {
        bitBlt(x, y, w, h, src, srcX, srcY)
    }

    // 自身内コピー (in-place)。CF_R=180°回転、CF_X=水平反転、CF_Y=垂直反転。
    fun copyRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        srcX: Int,
        srcY: Int,
        flag: CopyFlag,
    ) {
        // ソースを別バッファに退避してから書き込む (オーバーラップ対策)。
        val src = IntArray(w * h)
        for (yy in 0 until h) {
            System.arraycopy(pixels, (srcY + yy) * width + srcX, src, yy * w, w)
        }
        when (flag) {
            CopyFlag.CF_R -> {
                // 180°回転: dest(x+w-1-i, y+h-1-j) ← src(i, j)
                for (j in 0 until h) {
                    val dstRow = (y + h - 1 - j) * width + (x + w - 1)
                    val srcRow = j * w
                    for (i in 0 until w) {
                        pixels[dstRow - i] = src[srcRow + i]
                    }
                }
            }
            CopyFlag.CF_X -> {
                // 水平反転: dest(x+w-1-i, y+j) ← src(i, j)
                for (j in 0 until h) {
                    val dstRow = (y + j) * width + (x + w - 1)
                    val srcRow = j * w
                    for (i in 0 until w) {
                        pixels[dstRow - i] = src[srcRow + i]
                    }
                }
            }
            CopyFlag.CF_Y -> {
                // 垂直反転: dest(x+i, y+h-1-j) ← src(i, j)
                for (j in 0 until h) {
                    val dstRow = (y + h - 1 - j) * width + x
                    val srcRow = j * w
                    for (i in 0 until w) {
                        pixels[dstRow + i] = src[srcRow + i]
                    }
                }
            }
        }
    }

    // src.alpha をマスクとして color で線形補間合成 (alpha=max → color、alpha=0 → 透過)。
    fun copyAlpha(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        src: MemDC,
        srcX: Int,
        srcY: Int,
        color: Int,
    ) {
        val alp = src.alpha ?: error("source MemDC has no alpha map")
        val lv = alp.level
        val crb = color and 0xff00ff
        val cgg = color and 0x00ff00
        val sn = alp.width
        for (yy in 0 until h) {
            val dstRow = (y + yy) * width + x
            val srcRow = (srcY + yy) * sn + srcX
            for (xx in 0 until w) {
                val a = alp.map[srcRow + xx].toInt() and 0xffff
                var rb = pixels[dstRow + xx] and 0xff00ff
                var gg = pixels[dstRow + xx] and 0x00ff00
                rb += ((crb - rb) * a) shr lv
                gg += ((cgg - gg) * a) shr lv
                pixels[dstRow + xx] = (rb and 0xff00ff) or (gg and 0x00ff00)
            }
        }
    }

    fun drawRect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        color: Int,
    ) {
        for (yy in y until y + h) {
            val row = yy * width
            for (xx in x until x + w) {
                pixels[row + xx] = color
            }
        }
    }

    // 2^level x 2^level ピクセルブロックごとに非 0 画素を数え、その値をアルファセルとして焼き込む。
    // 出力アルファマップは width/height を 2^level で縮小。copyAlpha は (alp.level) bit シフトで割る。
    fun setAlpha(level: Int = 4) {
        val w = width shr level
        val h = height shr level
        val map = ShortArray(w * h)
        for (y in 0 until height) {
            val row = y * width
            val yy = w * (y shr level)
            for (x in 0 until width) {
                if (pixels[row + x] != 0) {
                    val idx = (x shr level) + yy
                    map[idx] = (map[idx].toInt() + 1).toShort()
                }
            }
        }
        var alpLevel = level * 2
        if (level > 4) {
            // short が溢れない範囲に収めるため level=8 まで丸める。
            val dif = alpLevel - 8
            for (i in map.indices) {
                map[i] = ((map[i].toInt() and 0xffff) shr dif).toShort()
            }
            alpLevel = 8
        }
        alpha = AlphaData(w, h, alpLevel, map)
    }

    // rate x rate ブロックの平均値で 1/rate にダウンサンプル。
    fun reducedImage(rate: Int) {
        val w = width / rate
        val h = height / rate
        val rb = LongArray(w * h)
        val gg = LongArray(w * h)
        for (sy in 0 until h * rate) {
            val dy = sy / rate * w
            val srcRow = sy * width
            for (sx in 0 until w * rate) {
                val dz = sx / rate + dy
                val p = pixels[srcRow + sx]
                rb[dz] += (p and 0xff00ff).toLong()
                gg[dz] += (p and 0x00ff00).toLong()
            }
        }
        setBitmap(w, h)
        val divisor = (rate * rate).toLong()
        for (i in 0 until w * h) {
            val fixRb = ((rb[i] / divisor).toInt()) and 0xff00ff
            val fixGg = ((gg[i] / divisor).toInt()) and 0x00ff00
            pixels[i] = fixRb or fixGg
        }
    }

    // 画像ファイルを読み込んで自分のバッファに展開する。指定拡張子が無ければ別の画像拡張子を試し、
    // それでも見つからない場合は 1x1 のプレースホルダにする (呼び出し側でサイズ検査して error する想定)。
    fun loadImage(file: File) {
        val resolved = resolveImage(file)
        if (resolved == null) {
            System.err.println("MemDC.loadImage: not found '${file.path}', using 1x1 placeholder")
            setBitmap(1, 1)
            return
        }
        val img =
            ImageIO.read(resolved)
                ?: error("MemDC.loadImage: ImageIO returned null for ${resolved.path}")
        loadImage(img)
    }

    fun loadImage(img: BufferedImage) {
        setBitmap(img.width, img.height)
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, img.width, img.height)
        graphics.drawImage(img, 0, 0, null)
    }

    fun saveImage(file: File) {
        file.parentFile?.mkdirs()
        if (!ImageIO.write(image, "PNG", file)) error("MemDC.saveImage: no PNG writer available")
    }

    // 自身のビットマップ (非0画素=不透明黒) を targetSize 角の ARGB PNG として書き出す。
    // bbox トリミング後、アスペクト比を保って長辺を targetSize に揃え、短辺は中央寄せ (透過余白)。
    fun saveIconPng(
        file: File,
        targetSize: Int,
    ) {
        val rc = getBorder()
        val sw = rc.right - rc.left + 1
        val sh = rc.bottom - rc.top + 1

        // alpha = (非0なら不透明), rgb = 0 (黒)。rgb を 0 で揃えると BICUBIC 時の色境界滲みが出ない。
        val source = BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB)
        val srcData = (source.raster.dataBuffer as DataBufferInt).data
        for (y in 0 until sh) {
            val srcRow = (rc.top + y) * width + rc.left
            val dstRow = y * sw
            for (x in 0 until sw) {
                srcData[dstRow + x] = if ((pixels[srcRow + x] and 0xffffff) != 0) 0xff000000.toInt() else 0
            }
        }

        // アスペクト比を保ちつつ targetSize x targetSize に収める。
        val scale = minOf(targetSize.toDouble() / sw, targetSize.toDouble() / sh)
        val dw = (sw * scale).toInt().coerceAtMost(targetSize)
        val dh = (sh * scale).toInt().coerceAtMost(targetSize)
        val ox = (targetSize - dw) / 2
        val oy = (targetSize - dh) / 2

        val out = BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.drawImage(source, ox, oy, dw, dh, null)
        } finally {
            g.dispose()
        }

        file.parentFile?.mkdirs()
        if (!ImageIO.write(out, "PNG", file)) {
            error("MemDC.saveIconPng: no PNG writer available")
        }
    }

    // アルファマップを RGBA PNG として書き出す。
    // 横幅を cellColors.size 等分し、各セルの色 (TYPE_INT_RGB) を RGB に置き、A チャネルはマップ値そのまま。
    // setAlpha が level=8 に正規化済みなのでアルファ値は 1 バイトに収まる。
    fun saveAlphaPng(
        file: File,
        cellColors: IntArray,
    ) {
        val alp = alpha ?: error("MemDC.saveAlphaPng: alpha not set")
        val n = cellColors.size
        if (n <= 0 || alp.width % n != 0) {
            error("MemDC.saveAlphaPng: alpha width ${alp.width} is not divisible by cellColors.size $n")
        }
        file.parentFile?.mkdirs()

        val w = alp.width
        val h = alp.height
        val cellW = w / n
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val out = (img.raster.dataBuffer as DataBufferInt).data
        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = (alp.map[y * w + x].toInt() and 0xffff).coerceAtMost(0xff)
                val rgb = cellColors[x / cellW] and 0xffffff
                out[y * w + x] = (a shl 24) or rgb
            }
        }
        if (!ImageIO.write(img, "PNG", file)) {
            error("MemDC.saveAlphaPng: no PNG writer available")
        }
    }

    private fun createBuffer(
        w: Int,
        h: Int,
    ): BufferedImage = BufferedImage(maxOf(w, 1), maxOf(h, 1), BufferedImage.TYPE_INT_RGB)

    private fun newGraphics(img: BufferedImage): Graphics2D {
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
        g.color = Color.WHITE
        return g
    }

    private fun resolveImage(file: File): File? {
        if (file.exists()) return file
        val parent = file.parentFile ?: return null
        val baseName = file.nameWithoutExtension
        for (ext in listOf("png", "gif", "bmp", "jpg", "jpeg")) {
            val candidate = File(parent, "$baseName.$ext")
            if (candidate.exists()) return candidate
        }
        return null
    }

    companion object {
        private val availableFamilies: Set<String> by lazy {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }
        private val warnedFonts = mutableSetOf<String>()

        fun resolveFontFamily(name: String): String {
            if (name in availableFamilies) return name
            if (warnedFonts.add(name)) {
                System.err.println("MemDC: font '$name' not available, falling back to default")
            }
            return Font.SANS_SERIF
        }
    }
}
