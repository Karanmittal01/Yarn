package app.yarn.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.LruCache
import androidx.core.content.ContextCompat
import app.yarn.intelligence.SenderNames
import app.yarn.telephony.PhoneNumbers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ContactInfo(
    val name: String,
    val photoUri: String?,
    val lookupUri: Uri?,
    val starred: Boolean,
)

data class ContactPhone(
    val contactId: Long,
    val name: String,
    val number: String,
    val typeLabel: String,
    val photoUri: String?,
    val starred: Boolean,
)

class ContactsRepository(private val context: Context) {
    private val cache = LruCache<String, Optional>(1000)
    private val _version = MutableStateFlow(0)

    /** Bumps whenever the device contacts change so UI and caches can refresh names. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private class Optional(val value: ContactInfo?)

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            cache.evictAll()
            _version.value++
        }
    }

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (observing || !hasPermission()) return
        runCatching {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
            cache.evictAll()
            _version.value++
        }
    }

    fun lookup(address: String): ContactInfo? {
        if (!hasPermission() || address.isBlank()) return null
        val key = PhoneNumbers.matchKey(address)
        cache.get(key)?.let { return it.value }
        val info = if (PhoneNumbers.isEmail(address)) lookupEmail(address) else if (PhoneNumbers.isDialable(address)) lookupPhone(address) else null
        cache.put(key, Optional(info))
        return info
    }

    private fun lookupPhone(number: String): ContactInfo? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.STARRED),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            ContactInfo(
                name = c.getString(0).orEmpty(),
                photoUri = c.getString(1),
                lookupUri = ContactsContract.Contacts.getLookupUri(c.getLong(3), c.getString(2)),
                starred = c.getInt(4) != 0,
            )
        }
    }.getOrNull()

    private fun lookupEmail(email: String): ContactInfo? = runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(email))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.STARRED),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContactInfo(c.getString(0).orEmpty(), c.getString(1), null, c.getInt(2) != 0) else null }
    }.getOrNull()

    fun displayName(addresses: List<String>, iso: String): String =
        addresses.joinToString(", ") { a ->
            lookup(a)?.name?.takeIf { it.isNotBlank() } ?: SenderNames.pretty(a) ?: PhoneNumbers.format(a, iso)
        }

    /** Phone numbers matching [query] by name or number, starred first. */
    fun search(query: String, limit: Int = 60): List<ContactPhone> {
        if (!hasPermission()) return emptyList()
        val base = if (query.isBlank()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query))
        val out = ArrayList<ContactPhone>()
        val seen = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                base,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL, ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                    ContactsContract.CommonDataKinds.Phone.STARRED,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(2) ?: continue
                    val key = c.getLong(0).toString() + PhoneNumbers.matchKey(number)
                    if (!seen.add(key)) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
                    out += ContactPhone(c.getLong(0), c.getString(1).orEmpty(), number, label, c.getString(5), c.getInt(6) != 0)
                }
            }
        }
        return out
    }
}
