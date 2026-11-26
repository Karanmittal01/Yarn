package app.yarn.ai

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.yarn.data.contacts.ContactsRepository
import app.yarn.data.db.BlockRuleEntity
import app.yarn.data.db.MessageEntity
import app.yarn.data.db.MessageStatus
import app.yarn.data.db.YarnDatabase
import app.yarn.data.prefs.SettingsRepository
import app.yarn.intelligence.SpamVerdict
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A keyword filter the user set keeps its messages in Spam through every re-sort, on Free too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class KeywordFilterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: YarnDatabase
    // The engine's background watchers outlive each test's database; their errors stay in this test.
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> })
    private val settings by lazy { SettingsRepository(context) }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, YarnDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun engine(pro: Boolean) = IntelligenceEngine(db, settings, ContactsRepository(context), scope) { pro }

    private fun message(body: String) = MessageEntity(
        conversationId = 1, address = "VM-LOANZY-P", body = body, date = 1_000, outgoing = false, status = MessageStatus.RECEIVED,
    )

    @Test
    fun aReSortKeepsFilteredMessagesInSpam() = runTest {
        db.blocks().insert(BlockRuleEntity(type = BlockRuleEntity.KEYWORD, value = "instant loan", createdAt = 1))
        for (pro in listOf(true, false)) {
            val e = engine(pro)
            e.reload()
            val (filtered, _) = e.annotate(message("Get an INSTANT LOAN of Rs 5 lakh today, no documents"))
            assertThat(filtered.spamVerdict).isEqualTo(SpamVerdict.SPAM.name)
            assertThat(filtered.category).isEqualTo("SPAM")
            assertThat(filtered.spamReasons).isEqualTo(IntelligenceEngine.KEYWORD_REASON)
            val (clean, _) = e.annotate(message("Your order has been shipped"))
            assertThat(clean.spamVerdict).isNotEqualTo(SpamVerdict.SPAM.name)
        }
    }

    @Test
    fun yourOwnMessagesAreNeverFiltered() = runTest {
        db.blocks().insert(BlockRuleEntity(type = BlockRuleEntity.KEYWORD, value = "loan", createdAt = 1))
        val e = engine(true)
        e.reload()
        val (mine, _) = e.annotate(message("Did you get the loan?").copy(outgoing = true, address = "+919800000001"))
        assertThat(mine.spamVerdict).isNotEqualTo(SpamVerdict.SPAM.name)
    }
}
