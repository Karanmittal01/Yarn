package app.yarn.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.db.AttachmentEntity
import app.yarn.data.db.ConversationEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.BIN_OFF
import app.yarn.data.prefs.SettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RecycleBinTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: YarnDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var bin: RecycleBin

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, YarnDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        bin = RecycleBin(context, db, settings)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun message(id: Long, body: String, date: Long = 1_000): MessageEntity {
        db.conversations().upsert(ConversationEntity(id = 3, addresses = listOf("+919800000001"), displayName = "Karan Mittal"))
        val m = MessageEntity(id = id, conversationId = 3, providerId = id * 10, address = "+919800000001", body = body, date = date, outgoing = false, status = MessageStatus.RECEIVED, category = "PERSONAL")
        db.messages().insert(m)
        return m
    }

    @Test
    fun keepsACopyWithItsChatAndFiles() = runTest {
        settings.update { it.copy(binDays = 7) }
        val photo = File(context.filesDir, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val m = message(1, "See you at 7")
        db.messages().insertAttachments(listOf(AttachmentEntity(messageId = 1, mimeType = "image/jpeg", uri = android.net.Uri.fromFile(photo).toString(), fileName = "photo.jpg")))
        val ids = bin.keep(listOf(m.copy(hasAttachments = true)), now = 50_000)
        val kept = db.bin().getAll(ids).single()
        assertThat(kept.title).isEqualTo("Karan Mittal")
        assertThat(kept.body).isEqualTo("See you at 7")
        // The file is copied, so it survives the original being deleted.
        photo.delete()
        val copy = File(android.net.Uri.parse(kept.attachments.single().uri).path!!)
        assertThat(copy.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        bin.deleteForever(ids)
        assertThat(copy.exists()).isFalse()
        assertThat(bin.count.first()).isEqualTo(0)
    }

    @Test
    fun emptiesItselfAfterTheChosenDays() = runTest {
        settings.update { it.copy(binDays = 7) }
        val day = RecycleBin.DAY_MS
        bin.keep(listOf(message(1, "old")), now = 0)
        bin.keep(listOf(message(2, "new")), now = 6 * day)
        assertThat(bin.emptyExpired(now = 8 * day)).isEqualTo(1)
        assertThat(db.bin().all().map { it.body }).containsExactly("new")
    }

    @Test
    fun turnedOffKeepsNothing() = runTest {
        settings.update { it.copy(binDays = BIN_OFF) }
        assertThat(bin.keep(listOf(message(1, "gone")))).isEmpty()
        assertThat(db.bin().all()).isEmpty()
    }
}
