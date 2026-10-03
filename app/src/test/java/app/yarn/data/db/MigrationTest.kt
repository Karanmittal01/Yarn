package app.yarn.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Builds a real version-1 database from the exported schema and upgrades it, as on users' phones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun createVersion1(name: String) {
        val schema = JSONObject(File("schemas/app.yarn.data.db.YarnDatabase/1.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) db.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table)) }
                e.optJSONArray("contentSyncTriggers")?.let { t -> for (j in 0 until t.length()) db.execSQL(t.getString(j)) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO conversations (id, addresses, displayName, customTitle, snippet, snippetFromMe, snippetStatus, lastMessageAt, unreadCount, messageCount, pinned, pinnedAt, starred, archived, muted, blocked, category, categoryLocked, spam, importance, hasFailed, draft, draftUpdatedAt, preferredSubId, hasBusinessSender, hasOutgoing) VALUES (7, '[\"JD-HDFCBK\"]', 'HDFC Bank', NULL, 'Rs 500 debited', 0, 0, 2000, 1, 2, 0, 0, 0, 0, 0, 0, 'BANKING', 0, 0, 0, 0, NULL, 0, -1, 1, 0)")
            db.execSQL("INSERT INTO messages (conversationId, kind, address, body, date, dateSent, outgoing, status, errorCode, attempts, nextAttemptAt, scheduledAt, partsTotal, partsSent, partsFailed, partsDelivered, subId, read, seen, starred, hasAttachments, category, categorySource, categoryConfidence, categoryReasons, spamVerdict, spamScore, spamReasons, riskyUrls, priority, important, analyzed, mmsExpiry, mmsSize, deliveryReport, sendAsMms) VALUES (7, 0, 'JD-HDFCBK', 'old', 1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 'BANKING', 'RULES', 0, '', 'CLEAN', 0, '', '', 0, 0, 1, 0, 0, 0, 0)")
            db.execSQL("INSERT INTO messages (conversationId, kind, address, body, date, dateSent, outgoing, status, errorCode, attempts, nextAttemptAt, scheduledAt, partsTotal, partsSent, partsFailed, partsDelivered, subId, read, seen, starred, hasAttachments, category, categorySource, categoryConfidence, categoryReasons, spamVerdict, spamScore, spamReasons, riskyUrls, priority, important, analyzed, mmsExpiry, mmsSize, deliveryReport, sendAsMms) VALUES (7, 0, 'JD-HDFCBK', 'Rs 500 debited', 2000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 'BANKING', 'RULES', 0, '', 'CLEAN', 0, '', '', 0, 0, 1, 0, 0, 0, 0)")
            db.version = 1
        }
    }

    @Test
    fun upgradesToVersion2AndKeepsData() = runTest {
        createVersion1("migrate.db")
        val db = Room.databaseBuilder(context, YarnDatabase::class.java, "migrate.db")
            .addMigrations(YarnDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val c = db.conversations().get(7)!!
        assertThat(c.displayName).isEqualTo("HDFC Bank")
        assertThat(c.lastSubId).isEqualTo(3) // SIM of the newest message
        assertThat(db.messages().recent(7, 10)).hasSize(2)
        db.close()
    }
}
