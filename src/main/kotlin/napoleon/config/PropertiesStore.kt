package napoleon.config

import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.reader
import kotlin.io.path.writer

private val PROPERTIES_PATH = Path("napoleon.properties")
private const val COMMENT = "Napoleon"

fun loadProperties(): Properties {
    val props = Properties()
    if (PROPERTIES_PATH.exists()) {
        PROPERTIES_PATH.reader().use { props.load(it) }
    }
    return props
}

fun storeProperties(modify: Properties.() -> Unit) {
    val props = loadProperties()
    props.modify()
    PROPERTIES_PATH.writer().use { props.store(it, COMMENT) }
}

fun Properties.getInt(
    key: String,
    default: Int,
): Int = getProperty(key, default.toString()).toInt()

fun Properties.getBoolean(
    key: String,
    default: Boolean,
): Boolean = getProperty(key, default.toString()).toBoolean()
