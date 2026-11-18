package app.yarn

import android.content.Context
import java.io.File

/**
 * Native code (Google's on-device models) can crash in a way no Kotlin code can catch, closing the whole
 * app. When a crash report shows one of these libraries failed, its feature is switched off so Yarn doesn't
 * crash again and again. An app update brings it back, in case the library or the phone was fixed.
 */
object NativeGuard {
    enum class Feature(val library: String, val label: String) {
        SMART_REPLY("libpredictor_jni.so", "Suggested replies (Google ML Kit)"),
        ENTITIES("libtextclassifier3_jni_tclib.so", "Dates, addresses and tracking numbers (Google ML Kit)"),
        LANGUAGE_ID("liblanguage_id_l2c_jni.so", "Language detection (Google ML Kit)"),
        TRANSLATE("libtranslate_jni.so", "Offline translation (Google ML Kit)"),
        LOCAL_LLM("libllm_inference_engine_jni.so", "On-device AI model (MediaPipe)"),
    }

    private var file: File? = null
    private var version = 0L
    @Volatile private var off: Set<Feature> = emptySet()

    fun install(context: Context, versionCode: Long = BuildConfig.VERSION_CODE.toLong()) {
        version = versionCode
        val f = File(context.filesDir, "crash/native_off.txt").also { it.parentFile?.mkdirs() }
        file = f
        // Lines are "FEATURE versionCode"; only switches made in this version still hold.
        off = runCatching {
            f.takeIf { it.exists() }?.readLines().orEmpty().mapNotNull { line ->
                val (name, v) = line.split(' ').takeIf { it.size == 2 } ?: return@mapNotNull null
                Feature.entries.firstOrNull { it.name == name }?.takeIf { v.toLongOrNull() == versionCode }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    fun allowed(feature: Feature): Boolean = feature !in off

    /** The feature whose library this is, if Yarn can switch it off. */
    fun featureFor(library: String): Feature? = Feature.entries.firstOrNull { it.library == library }

    fun disable(feature: Feature) {
        if (feature in off) return
        off = off + feature
        runCatching { file?.writeText(off.joinToString("\n") { "${it.name} $version" } + "\n") }
    }

    /** For tests. */
    internal fun reset() {
        off = emptySet()
        file?.delete()
    }
}
