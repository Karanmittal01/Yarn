package app.yarn.ai

import android.util.Log
import android.util.LruCache
import app.yarn.intelligence.Entity
import app.yarn.intelligence.EntityType
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.TrackingNumberEntity
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** Google ML Kit language identification (bundled model, fully offline). */
class LanguageService {
    private val identifier: LanguageIdentifier by lazy { LanguageIdentification.getClient() }

    /** BCP-47 tag or null when undetermined. */
    suspend fun identify(text: String): String? = runCatching {
        identifier.identifyLanguage(text.take(500)).await().takeIf { it != "und" }
    }.getOrNull()
}

/**
 * ML Kit on-device translation. Language packs (~30 MB each) download once — over Wi-Fi by default —
 * and translation then works fully offline.
 */
class TranslationService(private val language: LanguageService) {
    private val translators = LruCache<String, Translator>(3)
    private val lock = Mutex()

    sealed interface Result {
        data class Success(val text: String, val sourceLanguage: String) : Result
        data object SameLanguage : Result
        data object UnknownLanguage : Result
        data class Unsupported(val language: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun translate(text: String, targetTag: String, allowMobileData: Boolean): Result {
        val sourceTag = language.identify(text) ?: return Result.UnknownLanguage
        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: return Result.Unsupported(sourceTag)
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: return Result.Unsupported(targetTag)
        if (source == target) return Result.SameLanguage
        return try {
            val translator = lock.withLock {
                translators.get("$source>$target") ?: Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
                ).also { translators.put("$source>$target", it) }
            }
            val conditions = DownloadConditions.Builder().apply { if (!allowMobileData) requireWifi() }.build()
            translator.downloadModelIfNeeded(conditions).await()
            Result.Success(translator.translate(text).await(), sourceTag)
        } catch (e: Exception) {
            Log.w(TAG, "Translation failed", e)
            Result.Failed(e.message ?: "Translation failed")
        }
    }

    companion object {
        private const val TAG = "TranslationService"
    }
}

/** ML Kit Smart Reply: on-device, English only, declines sensitive topics by design. */
class SmartReplyService {
    private val generator by lazy { SmartReply.getClient() }

    data class Turn(val text: String, val timestamp: Long, val fromMe: Boolean, val senderId: String)

    suspend fun suggest(conversation: List<Turn>): List<String> {
        if (conversation.isEmpty() || conversation.last().fromMe) return emptyList()
        val messages = conversation.takeLast(10).map {
            if (it.fromMe) TextMessage.createForLocalUser(it.text, it.timestamp)
            else TextMessage.createForRemoteUser(it.text, it.timestamp, it.senderId)
        }
        return runCatching {
            val result = withTimeoutOrNull(3000) { generator.suggestReplies(messages).await() } ?: return emptyList()
            if (result.status == SmartReplySuggestionResult.STATUS_SUCCESS) result.suggestions.map { it.text } else emptyList()
        }.getOrDefault(emptyList())
    }
}

/**
 * ML Kit entity extraction complements the rule-based extractor with learned models for addresses,
 * date/times, flight and tracking numbers. The model (~5 MB per language) downloads on Wi-Fi.
 */
class MlEntityService {
    private val extractor: EntityExtractor by lazy {
        EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build())
    }
    @Volatile private var ready = false

    suspend fun ensureModel(allowMobileData: Boolean = false): Boolean {
        if (ready) return true
        return runCatching {
            if (!extractor.isModelDownloaded().await()) {
                val c = DownloadConditions.Builder().apply { if (!allowMobileData) requireWifi() }.build()
                extractor.downloadModelIfNeeded(c).await()
            }
            ready = true
            true
        }.getOrElse { false }
    }

    suspend fun isModelDownloaded(): Boolean = runCatching { extractor.isModelDownloaded().await() }.getOrDefault(false)

    suspend fun extract(text: String, referenceTime: Long, zone: ZoneId = ZoneId.systemDefault()): List<Entity> {
        if (!ready && !isModelDownloaded()) return emptyList()
        ready = true
        val params = EntityExtractionParams.Builder(text)
            .setReferenceTime(referenceTime)
            .setReferenceTimeZone(TimeZone.getTimeZone(zone))
            .setEntityTypesFilter(
                setOf(
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_ADDRESS,
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_DATE_TIME,
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_FLIGHT_NUMBER,
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_TRACKING_NUMBER,
                ),
            )
            .build()
        val annotations = runCatching { extractor.annotate(params).await() }.getOrElse { return emptyList() }
        return annotations.flatMap { a ->
            a.entities.mapNotNull { e ->
                when (e.type) {
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_ADDRESS ->
                        Entity(EntityType.ADDRESS, a.annotatedText, a.annotatedText, a.start, a.end)
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_DATE_TIME -> {
                        val dt = e.asDateTimeEntity() ?: return@mapNotNull null
                        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(dt.timestampMillis), zone)
                        val allDay = dt.dateTimeGranularity <= DateTimeEntity.GRANULARITY_DAY
                        val value = if (allDay) ldt.toLocalDate().toString() else ldt.withSecond(0).withNano(0).toString()
                        Entity(EntityType.DATE_TIME, a.annotatedText, value, a.start, a.end, if (allDay) mapOf("allDay" to "true") else emptyMap())
                    }
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_FLIGHT_NUMBER -> {
                        val f = e.asFlightNumberEntity() ?: return@mapNotNull null
                        Entity(EntityType.FLIGHT, a.annotatedText, f.airlineCode + f.flightNumber, a.start, a.end)
                    }
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_TRACKING_NUMBER -> {
                        val t = e.asTrackingNumberEntity() ?: return@mapNotNull null
                        val carrier = when (t.parcelCarrier) {
                            TrackingNumberEntity.CARRIER_FEDEX -> "FedEx"
                            TrackingNumberEntity.CARRIER_UPS -> "UPS"
                            TrackingNumberEntity.CARRIER_DHL -> "DHL"
                            TrackingNumberEntity.CARRIER_USPS -> "USPS"
                            else -> null
                        }
                        Entity(EntityType.TRACKING_NUMBER, a.annotatedText, t.parcelTrackingNumber, a.start, a.end, buildMap { carrier?.let { put("carrier", it) } })
                    }
                    else -> null
                }
            }
        }
    }
}
