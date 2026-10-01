package app.yarn.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun listToString(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun stringToList(value: String): List<String> = if (value.isEmpty()) emptyList() else json.decodeFromString(value)

    @TypeConverter
    fun mapToString(map: Map<String, String>): String = json.encodeToString(map)

    @TypeConverter
    fun stringToMap(value: String): Map<String, String> = if (value.isEmpty()) emptyMap() else json.decodeFromString(value)
}

@Database(
    entities = [
        ConversationEntity::class, MessageEntity::class, MessageFts::class, AttachmentEntity::class,
        ExtractedEntity::class, CorrectionEntity::class, SenderRuleEntity::class, BlockRuleEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YarnDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun learning(): LearningDao
    abstract fun blocks(): BlockDao

    companion object {
        private const val NAME = "yarn.db"
        private const val TAG = "YarnDatabase"

        /**
         * Opens the SQLCipher-encrypted database. The 256-bit passphrase is random per install and
         * wrapped by a non-exportable Android Keystore key, so a copied database file is useless
         * off-device. If the wrapping key is gone (e.g. data restored onto a new device) the local
         * cache is rebuilt from the system message store instead of failing.
         */
        fun open(context: Context): YarnDatabase {
            System.loadLibrary("sqlcipher")
            val key = try {
                DatabaseKey.getOrCreate(context)
            } catch (e: Exception) {
                Log.w(TAG, "Database key unavailable; resetting local cache", e)
                context.deleteDatabase(NAME)
                DatabaseKey.reset(context)
                DatabaseKey.getOrCreate(context)
            }
            val db = build(context, key)
            return try {
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                Log.w(TAG, "Encrypted database unreadable; recreating", e)
                db.close()
                context.deleteDatabase(NAME)
                build(context, key)
            }
        }

        private fun build(context: Context, key: ByteArray) =
            Room.databaseBuilder(context, YarnDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
    }
}

internal object DatabaseKey {
    private const val PREFS = "yarn_db_key"
    private const val PREF_KEY = "wrapped"
    private const val ALIAS = "yarn_db_wrapping_key"

    fun getOrCreate(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(PREF_KEY, null)
        if (wrapped != null) return unwrap(Base64.decode(wrapped, Base64.NO_WRAP))
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(PREF_KEY, Base64.encodeToString(wrap(raw), Base64.NO_WRAP)).commit()
        return raw
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wrap(raw: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(raw)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob, 0, 12))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}
