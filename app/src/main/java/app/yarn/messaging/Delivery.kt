package app.yarn.messaging

/**
 * Delivery reports, the way Android's own Messages app and Google Messages use them: one report per
 * recipient, asked for on the message's last part. Carriers often drop the reports for the other parts, so
 * waiting for every part left long messages (and anything with emoji or Hindi, 70 characters a part) on a
 * single tick forever.
 *
 * Which recipients have confirmed is kept as a bitmask, so a carrier sending the same report twice can't
 * count twice. Group messages with more than 30 recipients fall back to a count.
 */
internal object Delivery {
    private const val MAX_MASK = 30

    /** Recipients of an outgoing message ("a,b,c"); at least one. */
    fun recipients(address: String): Int = address.split(',').count { it.isNotBlank() }.coerceAtLeast(1)

    /** [delivered] after a report for [recipient] (0-based; -1 if unknown, from before this scheme). */
    fun record(delivered: Int, recipient: Int, recipients: Int): Int =
        if (recipients > MAX_MASK) delivered + 1 else delivered or (1 shl recipient.coerceIn(0, recipients - 1))

    fun complete(delivered: Int, recipients: Int): Boolean =
        if (recipients > MAX_MASK) delivered >= recipients else delivered == (1 shl recipients) - 1

    /** TP-Status from a status report: 0x00-0x1F delivered, 0x20-0x3F the network is still trying, 0x40+ failed. */
    enum class Report { DELIVERED, PENDING, FAILED }

    fun report(tpStatus: Int): Report = when {
        tpStatus < 0x20 -> Report.DELIVERED
        tpStatus < 0x40 -> Report.PENDING
        else -> Report.FAILED
    }
}
