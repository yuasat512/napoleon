package napoleon.config

class Config {
    var gameCount = 5
    var wait = 600
    var auto = false
    var playThrough = false
    var gameLog = true

    // 揮発性 (永続化しない)。--debug 起動時に AI の判断ログを出力するために使う。
    var debug = false

    // 揮発性。0 = nanoTime からシードを生成、非 0 = その値で再現性のある実行をする。
    var seed = 0

    // 揮発性。リプレイや --debug 時に全プレイヤーの手札とキティを表示する。
    var showAllCards = false

    fun load(): Config =
        apply {
            val props = loadProperties()
            gameCount = props.getInt("game", gameCount)
            wait = props.getInt("wait", wait)
            auto = props.getBoolean("auto", auto)
            playThrough = props.getBoolean("playThrough", playThrough)
            gameLog = props.getBoolean("gameLog", gameLog)
        }

    fun save() {
        storeProperties {
            setProperty("game", "$gameCount")
            setProperty("wait", "$wait")
            setProperty("auto", "$auto")
            setProperty("playThrough", "$playThrough")
            setProperty("gameLog", "$gameLog")
        }
    }
}
