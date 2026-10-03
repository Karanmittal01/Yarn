package app.yarn.ui.conversation

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import app.yarn.R

/** Intents for contextual actions. Nothing here needs extra permissions. */
object Actions {
    private fun start(context: Context, intent: Intent, @androidx.annotation.StringRes failure: Int = R.string.no_app) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(failure), Toast.LENGTH_SHORT).show()
        }
    }

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), R.string.no_browser)
    fun dial(context: Context, number: String) = start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    fun email(context: Context, address: String) = start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    fun map(context: Context, address: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))), R.string.no_maps)

    /** Opens the calendar's "new event" screen prefilled; the user confirms there. */
    fun addToCalendar(context: Context, isoValue: String, title: String, description: String) {
        val zone = ZoneId.systemDefault()
        val (begin, allDay) = runCatching {
            if (isoValue.contains('T')) LocalDateTime.parse(isoValue).atZone(zone).toInstant().toEpochMilli() to false
            else LocalDate.parse(isoValue).atStartOfDay(zone).toInstant().toEpochMilli() to true
        }.getOrElse { return }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + if (allDay) 0 else 3_600_000L)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
        start(context, intent, R.string.no_calendar)
    }

    /** A URI other apps can read: our files go through FileProvider; MMS parts are grantable. */
    fun shareableUri(context: Context, uri: Uri): Uri =
        if (uri.scheme == "file") FileProvider.getUriForFile(context, "${context.packageName}.files", File(uri.path!!)) else uri

    fun openAttachment(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(shareableUri(context, uri), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, intent, R.string.no_file_app)
    }

    fun share(context: Context, text: String, uris: List<Pair<Uri, String>>) {
        val intent = when {
            uris.isEmpty() -> Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            uris.size == 1 -> Intent(Intent.ACTION_SEND).setType(uris[0].second).putExtra(Intent.EXTRA_STREAM, shareableUri(context, uris[0].first))
                .apply { if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text) }
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.map { shareableUri(context, it.first) }))
                .apply { if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text) }
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(intent, context.getString(R.string.share)))
    }

    /** Saves an attachment to the public Downloads collection (no storage permission needed). */
    fun saveToDownloads(context: Context, uri: Uri, mime: String, name: String?): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name ?: "yarn_${System.currentTimeMillis()}")
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openInputStream(uri)!!.use { input -> resolver.openOutputStream(target)!!.use { input.copyTo(it) } }
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        true
    }.getOrDefault(false)
}
