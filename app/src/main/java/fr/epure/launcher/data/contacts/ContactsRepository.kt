package fr.epure.launcher.data.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/** Contact trouvé par la recherche, avec son numéro principal s'il en a un. */
data class ContactResult(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phone: String?,
) {
    val key: String get() = "contact:$id"
}

/**
 * Recherche dans les contacts du téléphone (avec l'autorisation « Contacts »). Rien n'est
 * copié ni envoyé : Épure interroge le carnet d'Android à chaque recherche.
 */
class ContactsRepository(context: Context) {

    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contacts dont un mot du nom commence par [query] (sans tenir compte des accents). À appeler hors du fil principal. */
    fun search(query: String, limit: Int = 4): List<ContactResult> {
        val text = query.trim()
        if (text.length < 2 || !hasPermission()) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(text))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )
        val found = ArrayList<ContactResult>()
        runCatching {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    val phone = if (cursor.getInt(3) > 0) primaryPhone(id) else null
                    found += ContactResult(id = id, lookupKey = cursor.getString(1).orEmpty(), name = name, phone = phone)
                }
            }
        }.onFailure { Log.w(TAG, "Recherche dans les contacts impossible", it) }
        return found
    }

    private fun primaryPhone(contactId: Long): String? = runCatching {
        appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** Fiche du contact dans l'app Contacts. */
    fun contactUri(contact: ContactResult): Uri =
        if (contact.lookupKey.isNotEmpty()) {
            ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        } else {
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
        }

    private companion object {
        const val TAG = "Contacts"
    }
}
