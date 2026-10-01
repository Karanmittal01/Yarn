package app.yarn.intelligence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnalyzerTest {
    private val analyzer = MessageAnalyzer()
    private fun msg(text: String, from: String, contact: Boolean = false) =
        MessageInput(text, SenderInfo(from, isContact = contact), System.currentTimeMillis())

    private fun cat(text: String, from: String, contact: Boolean = false) = analyzer.analyze(msg(text, from, contact)).category

    @Test
    fun categorisesCommonMessages() {
        assertThat(cat("482913 is your OTP to login to HDFC NetBanking. Do not share with anyone.", "VM-HDFCBK-S")).isEqualTo(Category.OTP)
        assertThat(cat("Rs.2,500.00 debited from A/c XX1234 on 05-Mar-25 to VPA swiggy@icici. Avl Bal Rs 10,234.", "AD-HDFCBK-T")).isEqualTo(Category.BANKING)
        assertThat(cat("Your Amazon order #402-1234567 has been shipped. Track your package: amzn.in/d/abc", "AX-AMAZON-S")).isAnyOf(Category.DELIVERY, Category.SHOPPING)
        assertThat(cat("Mega SALE! Flat 50% off on all shoes. Use code SHOE50. Shop now!", "VK-MYNTRA-P")).isEqualTo(Category.PROMOTIONS)
        assertThat(cat("hey are we still on for dinner tonight?", "+15550100", contact = true)).isEqualTo(Category.PERSONAL)
        assertThat(cat("Your electricity bill of Rs 1,240 is due on 12-Apr. Pay via app to avoid late fee.", "BESCOM")).isEqualTo(Category.BILLS)
        assertThat(cat("PNR 4521367890: Train 12951 Coach B2 Berth 34 confirmed. Departure 16:55", "IRCTCI")).isEqualTo(Category.TRAVEL)
        assertThat(cat("Standup moved to 10:30, client deadline is Friday", "+15550199", contact = true)).isEqualTo(Category.WORK)
    }

    @Test
    fun indianBrandSendersLandInTheRightGroup() {
        assertThat(cat("Dear Customer, Thank you for ordering from Zepto. Your order is about to be delivered. Amount paid Rs 349", "JM-ZEPTON-S")).isAnyOf(Category.SHOPPING, Category.DELIVERY)
        assertThat(cat("CDSL: Credit in a/c *89040843 through IPO/FPO for 107-GERMAN GREEN STEEL", "JX-CDSLTX-S")).isEqualTo(Category.BANKING)
        assertThat(SenderNames.pretty("JK-IDFCFB-S")).isEqualTo("IDFC FIRST Bank")
    }

    @Test
    fun detectsScams() {
        val scam = analyzer.analyze(msg("USPS: Your package is on hold due to incomplete address. Update now: usps-redelivery-help.top/track", "+12025550143"))
        assertThat(scam.spam.verdict).isEqualTo(SpamVerdict.SCAM)
        assertThat(scam.spam.riskyUrls).isNotEmpty()
        assertThat(scam.category).isEqualTo(Category.SPAM)

        val kyc = analyzer.analyze(msg("Dear customer your SBI account will be blocked today. Update your KYC click here http://bit.ly/3xYz", "+919812345678"))
        assertThat(kyc.spam.verdict).isEqualTo(SpamVerdict.SCAM)

        val lookalike = analyzer.analyze(msg("Your Pаypal account has unusual activity, log in here paypal-secure-login.com", "+447700900111"))
        assertThat(lookalike.spam.verdict).isEqualTo(SpamVerdict.SCAM)
    }

    @Test
    fun legitimateMessagesAreNotFlagged() {
        val otp = analyzer.analyze(msg("123456 is your Amazon OTP. Do not share it with anyone.", "AX-AMAZON-T"))
        assertThat(otp.spam.verdict.isFiltered).isFalse()
        assertThat(otp.isImportant).isTrue()

        val friend = analyzer.analyze(msg("Check this out https://bit.ly/abc lol", "+15550100", contact = true))
        assertThat(friend.spam.verdict.isFiltered).isFalse()

        val bank = analyzer.analyze(msg("Unusual login detected on your account. If this wasn't you call 1800-123-4567.", "VM-ICICIB-S"))
        assertThat(bank.spam.verdict.isFiltered).isFalse()
    }

    @Test
    fun learnsFromCorrections() {
        val text = "Your weekly yoga class schedule: Mon 7am, Wed 7am. Reply STOP to opt out."
        val base = MessageAnalyzer().analyze(msg(text, "YOGAHB")).category
        assertThat(base).isEqualTo(Category.PROMOTIONS)

        val learning = LearningState(listOf(Correction(text, "YOGAHB", SenderKind.ALPHANUMERIC, category = Category.PERSONAL)))
        val next = MessageAnalyzer(learning = learning)
            .analyze(msg("Your weekly yoga class schedule: Tue 6am, Thu 6am. Reply STOP to opt out.", "YOGAHB"))
        assertThat(next.category).isEqualTo(Category.PERSONAL)
        assertThat(next.classification.source).isEqualTo(Classification.Source.SIMILAR_CORRECTION)
    }

    @Test
    fun senderRulesAndTrustWin() {
        val learning = LearningState(
            senderCategories = mapOf(LearningState.normalizeAddress("VM-ACME-S") to Category.WORK),
            senderTrust = mapOf(LearningState.normalizeAddress("+12025550143") to true),
        )
        val a = MessageAnalyzer(learning = learning)
        assertThat(a.analyze(msg("Big sale today!", "VM-ACME-S")).category).isEqualTo(Category.WORK)
        assertThat(a.analyze(msg("Click here to claim your prize bit.ly/x", "+1 202 555 0143")).spam.verdict.isFiltered).isFalse()
    }

    @Test
    fun naiveBayesGeneralises() {
        val corrections = buildList {
            repeat(6) { add(Correction("Gym session booked for slot $it at FitHub", "FITHUB", SenderKind.ALPHANUMERIC, category = Category.PERSONAL, timestampMillis = it.toLong())) }
            repeat(6) { add(Correction("Invoice $it approved by finance, payment run scheduled", "ERPSYS", SenderKind.ALPHANUMERIC, category = Category.WORK, timestampMillis = it.toLong())) }
        }
        val state = LearningState(corrections)
        val post = state.categoryPosterior(msg("FitHub: your gym slot is booked", "FITHUB"))
        assertThat(post.getValue(Category.PERSONAL)).isGreaterThan(0.8f)
    }

    @Test
    fun summarizerSelectsInformativeSentences() {
        val t = 1_700_000_000_000L
        val s = ExtractiveSummarizer().summarize(
            listOf(
                SummaryMessage("Sam", "hey", t, false),
                SummaryMessage("Sam", "Are we still doing the trip to Goa next month?", t + 1, false),
                SummaryMessage("Me", "yes! I booked flights for 14 March, PNR ABC123", t + 2, true),
                SummaryMessage("Sam", "ok", t + 3, false),
                SummaryMessage("Sam", "Great, I'll pay you Rs 8,000 for my ticket by Friday", t + 4, false),
                SummaryMessage("Sam", "Should I book the hotel too?", t + 5, false),
            ),
        )
        assertThat(s.bullets.joinToString()).contains("booked flights")
        assertThat(s.keyDetails.joinToString()).contains("Rs 8,000")
        assertThat(s.awaitingReply).contains("book the hotel")
    }

    @Test
    fun quickReplies() {
        assertThat(QuickReplies.suggest("where are you?", Category.PERSONAL)).contains("On my way")
        assertThat(QuickReplies.suggest("Your OTP is 1234", Category.OTP)).isEmpty()
        assertThat(QuickReplies.suggest("The report looks fine.", Category.PERSONAL)).isEmpty()
    }

    @Test
    fun urlRiskDoesNotFlagOfficialOrInnocentDomains() {
        assertThat(UrlTools.assess("https://groups.google.com/abc").score).isLessThan(0.3f)
        assertThat(UrlTools.assess("https://www.amazon.in/gp/your-orders").score).isLessThan(0.3f)
        assertThat(UrlTools.assess("https://pineapple.com").score).isLessThan(0.3f)
        assertThat(UrlTools.assess("http://amaz0n-support.xyz/login").score).isAtLeast(0.6f)
    }
}
