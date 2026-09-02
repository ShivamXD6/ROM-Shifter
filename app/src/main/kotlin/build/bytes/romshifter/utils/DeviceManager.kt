package build.bytes.romshifter.utils

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.Log
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object DeviceManager {
    private val threadIdCache = mutableMapOf<Set<String>, Long>()

    fun getAvailableBackups(savedPath: String): Set<String> {
        val deviceDir = "$savedPath/Device"
        return Shell.cmd("ls \"$deviceDir\"").exec().out.toSet()
    }

    @SuppressLint("SdCardPath")
    private fun ensureTelephonyDirs(context: Context) {
        val telephonyPkg = "com.android.providers.telephony"
        val pkg = context.packageName

        listOf(
            "/data/user/0/$telephonyPkg/databases",
            "/data/user_de/0/$telephonyPkg/databases",
            "/data/user_de/0/$telephonyPkg/app_parts"
        ).forEach { dir ->
            Shell.cmd("mkdir -p $dir").exec()
            Shell.cmd("chown radio:radio $dir && chmod 771 $dir").exec()
            Shell.cmd("""if [ "$(ls -A $dir 2>/dev/null)" ]; then chown radio:radio $dir/* && chmod 660 $dir/*; fi""")
                .exec()
        }
        Shell.cmd("am force-stop $telephonyPkg").exec()
        Thread.sleep(500)

        if (!Shell.cmd("[ -f /data/user/0/$telephonyPkg/databases/mmssms.db ]").exec().isSuccess) {
            val systemSmsApp = listOf(
                "com.google.android.apps.messaging",
                "com.android.messaging",
                "com.android.mms"
            ).firstOrNull { Shell.cmd("pm list packages $it").exec().out.isNotEmpty() }
            if (systemSmsApp != null) {
                setDefaultSmsAppRoot(systemSmsApp)
                Thread.sleep(1000)
                setDefaultSmsAppRoot(pkg)
                Thread.sleep(500)
            }
        }
        Shell.cmd("content query --uri content://sms --projection _id --where \"1=0\"").exec()
    }

    fun setDefaultSmsAppRoot(pkg: String): Boolean {
        Log.d("DeviceManager", "Attempting to set $pkg as default SMS app via root roles")
        val roleCmd = Shell.cmd("cmd role add-role-holder android.app.role.SMS $pkg").exec()

        Shell.cmd("settings put secure sms_default_application $pkg").exec()
        Shell.cmd("appops set $pkg WRITE_SMS allow").exec()
        Shell.cmd("appops set $pkg SEND_SMS allow").exec()
        Shell.cmd("appops set $pkg RECEIVE_SMS allow").exec()
        Shell.cmd("appops set $pkg RECEIVE_MMS allow").exec()

        Shell.cmd("content query --uri content://settings/secure --where \"name='sms_default_application'\"")
            .exec()

        return roleCmd.isSuccess
    }

    private fun getValidColumns(context: Context, uri: Uri): Set<String> {
        val q = {
            context.contentResolver.query(uri, null, "1=0", null, null)
                ?.use { it.columnNames.toSet() }
        }
        return try {
            q() ?: emptySet()
        } catch (_: Exception) {
            if (uri.toString().contains("sms") || uri.toString().contains("mms")) {
                ensureTelephonyDirs(context)
                try {
                    q() ?: emptySet()
                } catch (_: Exception) {
                    emptySet()
                }
            } else emptySet()
        }
    }

    private fun decodeQuotedPrintable(input: String): String {
        return try {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '=') {
                    val hex = input.substring(i + 1, i + 3); out.write(hex.toInt(16)); i += 3
                } else {
                    out.write(c.code); i++
                }
            }
            out.toString("UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    private fun importVcf(context: Context, vcfFile: File, updateProgress: (Int) -> Unit) {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            val unfoldedLines = mutableListOf<String>()
            var cur: StringBuilder? = null
            vcfFile.forEachLine { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) cur?.append(
                    line.substring(
                        1
                    )
                ) else {
                    cur?.let { unfoldedLines.add(it.toString()) }; cur = StringBuilder(line)
                }
            }
            cur?.let { unfoldedLines.add(it.toString()) }
            var name: String? = null
            val phones = mutableListOf<String>()
            var photo: ByteArray? = null
            val total = unfoldedLines.size
            unfoldedLines.forEachIndexed { i, line ->
                when {
                    line.startsWith("BEGIN:VCARD", true) -> {
                        name = null; phones.clear(); photo = null
                    }

                    line.startsWith("FN:", true) || line.startsWith("FN;", true) -> {
                        val v = line.substringAfter(":").trim(); name = if (line.contains(
                                "ENCODING=QUOTED-PRINTABLE",
                                true
                            )
                        ) decodeQuotedPrintable(v) else v
                    }

                    (line.startsWith("N:", true) || line.startsWith(
                        "N;",
                        true
                    )) && name == null -> {
                        val v = line.substringAfter(":").trim()
                        val d = if (line.contains(
                                "ENCODING=QUOTED-PRINTABLE",
                                true
                            )
                        ) decodeQuotedPrintable(v) else v; name =
                            d.split(";").filter { it.isNotBlank() }.joinToString(" ")
                    }

                    line.startsWith("TEL", true) -> {
                        val n = line.substringAfter(":").trim(); if (n.isNotEmpty()) phones.add(n)
                    }

                    line.startsWith("PHOTO", true) -> {
                        try {
                            photo = android.util.Base64.decode(
                                line.substringAfter(":").trim(),
                                android.util.Base64.DEFAULT
                            )
                        } catch (_: Exception) {
                        }
                    }

                    line.startsWith("END:VCARD", true) -> {
                        if (!name.isNullOrEmpty() || phones.isNotEmpty()) {
                            val rIdx = ops.size
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                                    .build()
                            )
                            ops.add(
                                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(
                                        ContactsContract.Data.RAW_CONTACT_ID,
                                        rIdx
                                    ).withValue(
                                        ContactsContract.Data.MIMETYPE,
                                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                                    ).withValue(
                                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                        name ?: "Unknown"
                                    ).build()
                            )
                            phones.forEach {
                                ops.add(
                                    ContentProviderOperation.newInsert(
                                        ContactsContract.Data.CONTENT_URI
                                    ).withValueBackReference(
                                        ContactsContract.Data.RAW_CONTACT_ID,
                                        rIdx
                                    ).withValue(
                                        ContactsContract.Data.MIMETYPE,
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                                    ).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, it)
                                        .withValue(
                                            ContactsContract.CommonDataKinds.Phone.TYPE,
                                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                        ).build()
                                )
                            }
                            photo?.let {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(
                                            ContactsContract.Data.RAW_CONTACT_ID,
                                            rIdx
                                        ).withValue(
                                            ContactsContract.Data.MIMETYPE,
                                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                                        )
                                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, it)
                                        .build()
                                )
                            }
                        }
                        if (ops.size > 100) {
                            context.contentResolver.applyBatch(
                                ContactsContract.AUTHORITY,
                                ops
                            ); ops.clear()
                        }
                    }
                }
                if (i % 50 == 0 && total > 0) updateProgress(i * 100 / total)
            }
            if (ops.isNotEmpty()) context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY,
                ops
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun backupSms(context: Context, writer: JsonWriter) {
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, null, null, null, null)
            ?.use { cursor ->
                writer.name("sms").beginArray()
                while (cursor.moveToNext()) {
                    writer.beginObject()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_STRING -> writer.name(name).value(cursor.getString(i))
                            Cursor.FIELD_TYPE_INTEGER -> writer.name(name).value(cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> writer.name(name).value(cursor.getDouble(i))
                            Cursor.FIELD_TYPE_NULL -> writer.name(name).nullValue()
                            Cursor.FIELD_TYPE_BLOB -> writer.name(name).nullValue()
                        }
                    }
                    writer.endObject()
                }
                writer.endArray()
            }
    }

    private fun restoreSms(context: Context, reader: JsonReader, onProgress: (String) -> Unit) {
        Log.d("DeviceManager", "Starting SMS restoration...")
        val validCols = getValidColumns(context, Telephony.Sms.CONTENT_URI)
        var count = 0
        try {
            reader.beginArray()
            val batch = mutableListOf<ContentValues>()
            while (reader.hasNext()) {
                val values = ContentValues(); reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    val peek = reader.peek()
                    if (peek == JsonToken.NULL) {
                        reader.nextNull(); continue
                    }
                    if (name == "_id" || name == "thread_id" || (validCols.isNotEmpty() && !validCols.contains(
                            name
                        ))
                    ) {
                        reader.skipValue(); continue
                    }
                    when (peek) {
                        JsonToken.STRING -> values.put(name, reader.nextString())
                        JsonToken.NUMBER -> values.put(name, reader.nextLong())
                        JsonToken.BOOLEAN -> values.put(name, reader.nextBoolean())
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (validCols.contains("sub_id")) values.put("sub_id", -1)
                batch.add(values)
                if (batch.size >= 50) {
                    count += flushBatch(context, Telephony.Sms.CONTENT_URI, batch)
                    onProgress("Restoring Messages ($count)...")
                    batch.clear()
                }
            }
            reader.endArray()
            if (batch.isNotEmpty()) count += flushBatch(context, Telephony.Sms.CONTENT_URI, batch)
            Log.d("DeviceManager", "SMS restoration completed. Restored $count SMS.")
            onProgress("Restored $count Messages.")
        } catch (e: Exception) {
            Log.e("DeviceManager", "Failed to restore SMS", e)
            onProgress("Error: ${e.message}")
        }
    }

    private fun flushBatch(context: Context, uri: Uri, batch: List<ContentValues>): Int {
        val ops = ArrayList<ContentProviderOperation>()
        for (values in batch) ops.add(
            ContentProviderOperation.newInsert(uri).withValues(values).build()
        )
        return try {
            val results = context.contentResolver.applyBatch(uri.authority!!, ops)
            var successCount = 0; results.forEach { if (it.uri != null) successCount++ }
            if (successCount < batch.size) retryIndividually(
                context,
                uri,
                batch,
                getValidColumns(context, uri)
            ) else successCount
        } catch (_: Exception) {
            retryIndividually(context, uri, batch, getValidColumns(context, uri))
        }
    }

    private fun retryIndividually(
        context: Context,
        uri: Uri,
        batch: List<ContentValues>,
        validColumns: Set<String>
    ): Int {
        var successCount = 0
        val isSms = uri.toString().contains("sms")
        val isCall = uri.toString().contains("call")
        for (values in batch) {
            try {
                if (context.contentResolver.insert(uri, values) != null) {
                    successCount++; continue
                }
                val safeValues = ContentValues()
                if (isSms) {
                    listOf(
                        "address",
                        "body",
                        "date",
                        "type",
                        "read",
                        "date_sent",
                        "seen"
                    ).forEach {
                        if (values.containsKey(it)) safeValues.put(
                            it,
                            values.getAsString(it)
                        )
                    }
                    if (validColumns.contains("sub_id")) safeValues.put("sub_id", -1)
                } else if (isCall) {
                    listOf(
                        "number",
                        "date",
                        "duration",
                        "type",
                        "new"
                    ).forEach {
                        if (values.containsKey(it)) safeValues.put(
                            it,
                            values.getAsString(it)
                        )
                    }
                }
                if (safeValues.size() > 0 && context.contentResolver.insert(
                        uri,
                        safeValues
                    ) != null
                ) successCount++
            } catch (_: Exception) {
            }
        }
        return successCount
    }

    private fun backupMms(context: Context, writer: JsonWriter, partsDir: File) {
        context.contentResolver.query(Telephony.Mms.CONTENT_URI, null, null, null, null)
            ?.use { cursor ->
                writer.name("mms").beginArray()
                while (cursor.moveToNext()) {
                    try {
                        writeMmsObject(context, cursor, writer, partsDir)
                    } catch (e: Exception) {
                        Log.e("DeviceManager", "Failed to backup MMS object", e)
                    }
                }
                try {
                    backupBugleRcs(context, writer, partsDir)
                } catch (e: Exception) {
                    Log.e("DeviceManager", "Failed to backup Bugle RCS", e)
                }
                writer.endArray()
            }
    }

    private fun writeMmsObject(
        context: Context,
        cursor: Cursor,
        writer: JsonWriter,
        partsDir: File
    ) {
        writer.beginObject()
        val mmsId = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms._ID))
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            val type = cursor.getType(i)
            when (type) {
                Cursor.FIELD_TYPE_STRING -> writer.name(name).value(cursor.getString(i))
                Cursor.FIELD_TYPE_INTEGER -> writer.name(name).value(cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> writer.name(name).value(cursor.getDouble(i))
                Cursor.FIELD_TYPE_NULL -> writer.name(name).nullValue()
                Cursor.FIELD_TYPE_BLOB -> writer.name(name).nullValue()
            }
        }
        writer.name("parts").beginArray()
        context.contentResolver.query(
            "content://mms/part".toUri(),
            null,
            "mid = ?",
            arrayOf(mmsId.toString()),
            null
        )?.use { pCursor ->
            while (pCursor.moveToNext()) {
                writer.beginObject()
                val partId = pCursor.getLong(pCursor.getColumnIndexOrThrow("_id"))
                for (j in 0 until pCursor.columnCount) {
                    val pName = pCursor.getColumnName(j)
                    when (pCursor.getType(j)) {
                        Cursor.FIELD_TYPE_STRING -> writer.name(pName).value(pCursor.getString(j))
                        Cursor.FIELD_TYPE_INTEGER -> writer.name(pName).value(pCursor.getLong(j))
                        Cursor.FIELD_TYPE_FLOAT -> writer.name(pName).value(pCursor.getDouble(j))
                        Cursor.FIELD_TYPE_NULL -> writer.name(pName).nullValue()
                        Cursor.FIELD_TYPE_BLOB -> writer.name(pName).nullValue()
                    }
                }
                val ct = pCursor.getString(pCursor.getColumnIndexOrThrow("ct"))
                if (ct != "text/plain" && !pCursor.isNull(pCursor.getColumnIndexOrThrow("_data"))) {
                    val pfn = "part_$partId"; writer.name("backup_file_name").value(pfn)
                    try {
                        context.contentResolver.openInputStream("content://mms/part/$partId".toUri())
                            ?.use { i ->
                                File(partsDir, pfn).outputStream().use { o -> i.copyTo(o) }
                            }
                    } catch (_: Exception) {
                    }
                }
                writer.endObject()
            }
        }; writer.endArray()
        writer.name("addrs").beginArray()
        context.contentResolver.query(
            "content://mms/$mmsId/addr".toUri(),
            null,
            null,
            null,
            null
        )?.use { aCursor ->
            while (aCursor.moveToNext()) {
                writer.beginObject()
                for (j in 0 until aCursor.columnCount) {
                    val aName = aCursor.getColumnName(j)
                    when (aCursor.getType(j)) {
                        Cursor.FIELD_TYPE_STRING -> writer.name(aName).value(aCursor.getString(j))
                        Cursor.FIELD_TYPE_INTEGER -> writer.name(aName).value(aCursor.getLong(j))
                        Cursor.FIELD_TYPE_FLOAT -> writer.name(aName).value(aCursor.getDouble(j))
                        Cursor.FIELD_TYPE_NULL -> writer.name(aName).nullValue()
                        Cursor.FIELD_TYPE_BLOB -> writer.name(aName).nullValue()
                    }
                }
                writer.endObject()
            }
        }; writer.endArray(); writer.endObject()
    }

    private fun backupBugleRcs(context: Context, writer: JsonWriter, partsDir: File) {
        val msgPkg = "com.google.android.apps.messaging"
        val dbFile = File(context.cacheDir, "bugle_db_temp")
        if (!Shell.cmd("cp /data/data/$msgPkg/databases/bugle_db ${dbFile.absolutePath}")
                .exec().isSuccess
        ) return
        Shell.cmd("chmod 666 ${dbFile.absolutePath}").exec()
        val db = try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            return
        }
        val query =
            "SELECT m._id, m.received_timestamp, m.read, m.seen, m.message_status, m.conversation_id, m.self_id, (SELECT normalized_destination FROM participants WHERE _id = m.self_id) as my_number FROM messages m WHERE m.message_protocol = 3 AND m.sms_message_uri IS NULL"
        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val bugleId = cursor.getLong(0)
                val convId = cursor.getLong(5)
                val selfId = cursor.getLong(6)
                val myNumber = cursor.getString(7)
                var remoteNumber: String? = null
                db.rawQuery(
                    "SELECT p.normalized_destination FROM participants p JOIN conversation_to_participants ctp ON p._id = ctp.participant_id WHERE ctp.conversation_id = ? AND p._id != ? LIMIT 1",
                    arrayOf(convId.toString(), selfId.toString())
                ).use { if (it.moveToFirst()) remoteNumber = it.getString(0) }
                if (remoteNumber == null || remoteNumber.contains("@")) continue
                writer.beginObject(); writer.name("ct_t")
                    .value("application/vnd.wap.multipart.related")
                val isOutgoing = cursor.getInt(4) in listOf(2, 4, 100, 1)
                writer.name("m_type").value(if (isOutgoing) 128 else 132); writer.name("msg_box")
                    .value(if (isOutgoing) 2 else 1)
                writer.name("date").value(cursor.getLong(1) / 1000); writer.name("read")
                    .value(cursor.getInt(2)); writer.name("seen").value(cursor.getInt(3))
                writer.name("tr_id").value("rcs_$bugleId"); writer.name("m_cls")
                    .value("personal"); writer.name("v").value(18); writer.name("sub_id").value(-1)
                writer.name("parts").beginArray()
                db.rawQuery(
                    "SELECT text, content_type, local_cache_path FROM parts WHERE message_id = ?",
                    arrayOf(bugleId.toString())
                ).use { pCursor ->
                    while (pCursor.moveToNext()) {
                        val text = pCursor.getString(0)
                        val ct = pCursor.getString(1)
                        val cachePath = pCursor.getString(2)
                        writer.beginObject(); writer.name("ct").value(ct)
                        if (text != null) writer.name("text").value(text)
                        if (cachePath != null && ct != "text/plain") {
                            val pfn =
                                "part_rcs_${bugleId}_${pCursor.position}"; writer.name("backup_file_name")
                                .value(pfn)
                            Shell.cmd(
                                "cp \"$cachePath\" \"${
                                    File(
                                        partsDir,
                                        pfn
                                    ).absolutePath
                                }\" && chmod 666 \"${File(partsDir, pfn).absolutePath}\""
                            ).exec()
                        }
                        writer.endObject()
                    }
                }; writer.endArray()
                writer.name("addrs").beginArray(); writer.beginObject(); writer.name("address")
                    .value(remoteNumber); writer.name("type")
                    .value(if (isOutgoing) 151 else 137); writer.endObject(); writer.endArray()
                if (myNumber != null) writer.name("threading_self_number").value(myNumber)
                writer.endObject()
            }
        }; db.close(); dbFile.delete()
    }

    private fun forceInsertPart(mmsId: Long, values: ContentValues): Uri? {
        val cmd = StringBuilder("content insert --uri content://mms/$mmsId/part")
        if (!values.containsKey("mid")) values.put("mid", mmsId)
        for (key in values.keySet()) {
            val v = values.get(key) ?: continue
            if (key in listOf("_id", "sub_id", "_data")) continue
            cmd.append(" --bind ").append(key).append(":")
            when (v) {
                is String -> cmd.append("s:'").append(v.replace("'", "'\\''")).append("'")
                is Int -> cmd.append("i:").append(v)
                is Long -> cmd.append("l:").append(v)
                is Float -> cmd.append("f:").append(v)
                is Double -> cmd.append("d:").append(v)
                is Boolean -> cmd.append("b:").append(if (v) "1" else "0")
            }
        }
        val res = Shell.cmd(cmd.toString()).exec()
        if (res.isSuccess) {
            val out = res.out.joinToString(" ")
            val m = Regex("content://\\S+").find(out); if (m != null) return m.value.toUri()
        }
        return null
    }

    private fun getActiveSubId(context: Context): Int {
        return try {
            context.contentResolver.query(
                "content://telephony/siminfo".toUri(),
                arrayOf("_id"),
                null,
                null,
                null
            )?.use { if (it.moveToFirst()) it.getInt(0) else -1 } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    @SuppressLint("HardwareIds")
    private fun getSelfNumbers(context: Context): Set<String> {
        val nums = mutableSetOf<String>()
        try {
            val sm =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
            @Suppress("MissingPermission")
            sm?.activeSubscriptionInfoList?.forEach { sub ->
                @Suppress("DEPRECATION")
                sub.number?.let { if (it.isNotBlank()) nums.add(it) }
            }
        } catch (_: SecurityException) {
            Log.w("DeviceManager", "Missing READ_PHONE_STATE for SubscriptionManager")
        } catch (_: Exception) {
        }
        try {
            val tm =
                context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            @Suppress("DEPRECATION")
            tm?.line1Number?.let { if (it.isNotBlank()) nums.add(it) }
        } catch (_: SecurityException) {
            Log.w("DeviceManager", "Missing READ_PHONE_STATE for TelephonyManager")
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.query(
                "content://telephony/siminfo".toUri(),
                arrayOf("number"),
                null,
                null,
                null
            )?.use {
                while (it.moveToNext()) it.getString(0)
                    ?.let { n -> if (n.isNotBlank()) nums.add(n) }
            }
        } catch (_: Exception) {
        }
        val processed = nums.map { it.replace(Regex("[^0-9]"), "") }.filter { it.length >= 7 }
            .map { it.takeLast(10) }.toMutableSet()
        Log.d("DeviceManager", "Identified initial self number suffixes: $processed")
        return processed
    }

    private fun isSelfNumber(addr: String?, selfSuffixes: Set<String>): Boolean {
        if (addr.isNullOrBlank() || addr.contains("@")) return false
        val clean = addr.replace(Regex("[^0-9]"), "")
        if (clean.length < 7) return false
        return selfSuffixes.any { clean.endsWith(it) }
    }

    private fun getOrCreateThreadId(context: Context, addresses: List<String>): Long {
        if (addresses.isEmpty()) return -1
        val key = addresses.toSet(); threadIdCache[key]?.let { return it }
        return try {
            val tid = Telephony.Threads.getOrCreateThreadId(context, key); threadIdCache[key] =
                tid; tid
        } catch (e: Exception) {
            if (e.message?.contains("no such table", true) == true) ensureTelephonyDirs(context)
            try {
                val tid = Telephony.Threads.getOrCreateThreadId(context, key); threadIdCache[key] =
                    tid; tid
            } catch (_: Exception) {
                -1
            }
        }
    }

    private fun forceInsertHeader(uri: Uri, values: ContentValues): Uri? {
        val cmd = StringBuilder("content insert --uri $uri")
        for (key in values.keySet()) {
            val v = values.get(key) ?: continue
            if (key in listOf("_id", "thread_id", "creator")) continue
            cmd.append(" --bind ").append(key).append(":")
            when (v) {
                is String -> cmd.append("s:'").append(v.replace("'", "'\\''")).append("'")
                is Int -> cmd.append("i:").append(v)
                is Long -> cmd.append("l:").append(v)
                is Float -> cmd.append("f:").append(v)
                is Double -> cmd.append("d:").append(v)
                is Boolean -> cmd.append("b:").append(if (v) "1" else "0")
            }
        }
        val res = Shell.cmd(cmd.toString()).exec()
        if (res.isSuccess) {
            val out = res.out.joinToString(" ")
            val m = Regex("content://\\S+").find(out); if (m != null) return m.value.toUri()
        }
        return null
    }

    private fun restoreMms(
        context: Context,
        reader: JsonReader,
        partsDir: File,
        onProgress: (String) -> Unit
    ) {
        Log.d("DeviceManager", "Starting MMS restoration...")
        val selfSuffixes = getSelfNumbers(context).toMutableSet()
        val activeSubId = getActiveSubId(context)
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val mmsValues = ContentValues()
            val parts = mutableListOf<ContentValues>()
            val addrs = mutableListOf<ContentValues>()
            val partFiles = mutableMapOf<Int, File>()
            reader.beginObject()
            while (reader.hasNext()) {
                when (val name = reader.nextName()) {
                    "parts" -> {
                        reader.beginArray()
                        var pIdx = 0
                        while (reader.hasNext()) {
                            val pValues = ContentValues(); reader.beginObject()
                            while (reader.hasNext()) {
                                val pName = reader.nextName()
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull(); continue
                                }
                                if (pName == "_id" || pName == "backup_file_name") {
                                    val valStr =
                                        if (reader.peek() == JsonToken.NUMBER) reader.nextLong()
                                            .toString() else reader.nextString()
                                    val fName = if (pName == "_id") "part_$valStr" else valStr
                                    val f = File(partsDir, fName)
                                    if (f.exists()) partFiles[pIdx] = f; continue
                                }
                                when (reader.peek()) {
                                    JsonToken.STRING -> pValues.put(pName, reader.nextString())
                                    JsonToken.NUMBER -> pValues.put(pName, reader.nextLong())
                                    JsonToken.BOOLEAN -> pValues.put(pName, reader.nextBoolean())
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject(); parts.add(pValues); pIdx++
                        }
                        reader.endArray()
                    }

                    "addrs" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val aValues = ContentValues(); reader.beginObject()
                            while (reader.hasNext()) {
                                val aName = reader.nextName()
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull(); continue
                                }
                                if (aName == "_id" || aName == "msg_id") {
                                    reader.skipValue(); continue
                                }
                                when (reader.peek()) {
                                    JsonToken.STRING -> aValues.put(aName, reader.nextString())
                                    JsonToken.NUMBER -> aValues.put(aName, reader.nextLong())
                                    JsonToken.BOOLEAN -> aValues.put(aName, reader.nextBoolean())
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject(); addrs.add(aValues)
                        }
                        reader.endArray()
                    }

                    "_id", "thread_id", "threading_self_number" -> reader.skipValue()
                    else -> {
                        if (reader.peek() == JsonToken.NULL) reader.nextNull()
                        else when (reader.peek()) {
                            JsonToken.STRING -> mmsValues.put(name, reader.nextString())
                            JsonToken.NUMBER -> mmsValues.put(name, reader.nextLong())
                            JsonToken.BOOLEAN -> mmsValues.put(name, reader.nextBoolean())
                            else -> reader.skipValue()
                        }
                    }
                }
            }
            reader.endObject()
            val keysToClear = listOf(
                "_id",
                "thread_id",
                "creator",
                "tr_id",
                "m_id",
                "ct_cls",
                "rpt_a",
                "resp_st",
                "retr_st",
                "st",
                "threading_self_number",
                "m_size",
                "text_only",
                "locked",
                "msg_id"
            )
            keysToClear.forEach { mmsValues.remove(it) }
            mmsValues.put("read", 1); mmsValues.put("seen", 1)
            if (mmsValues.containsKey("sub_id")) mmsValues.put("sub_id", activeSubId)

            val msgBox = mmsValues.getAsInteger("msg_box") ?: 1

            addrs.forEach { a ->
                val addr = a.getAsString("address")
                val type = a.getAsInteger("type")
                if (addr != null && ((msgBox == 2 && type == 137) || (msgBox == 1 && type == 151))) {
                    val clean = addr.replace(Regex("[^0-9]"), "")
                    if (clean.length >= 7) selfSuffixes.add(clean.takeLast(10))
                }
            }

            val addrStrings = addrs.mapNotNull { it.getAsString("address") }
                .filter { it.isNotBlank() && !it.contains("@") && !isSelfNumber(it, selfSuffixes) }

            if (addrStrings.isNotEmpty()) {
                val tid = getOrCreateThreadId(context, addrStrings)
                if (tid != -1L) mmsValues.put("thread_id", tid)
            }

            val targetUri =
                if (msgBox == 1) "content://mms/inbox" else "content://mms/sent".toUri()
            try {
                var mmsUri = try {
                    context.contentResolver.insert(targetUri as Uri, mmsValues)
                } catch (e: Exception) {
                    Log.e("DeviceManager", "Standard insert failed for MMS", e); null
                }
                if (mmsUri == null) {
                    Log.d("DeviceManager", "Attempting force insert for MMS header")
                    mmsUri = forceInsertHeader(targetUri as Uri, mmsValues)
                }
                if (mmsUri == null) {
                    Log.e("DeviceManager", "Failed to insert MMS header")
                    continue
                }
                val mmsId = mmsUri.lastPathSegment?.toLongOrNull() ?: continue
                count++; onProgress("Restoring Messages ($count)...")
                Log.d(
                    "DeviceManager",
                    "Restored MMS header ID: $mmsId, now restoring ${parts.size} parts and ${addrs.size} addresses"
                )

                parts.forEachIndexed { index, pValues ->
                    listOf("_id", "sub_id", "_data").forEach { pValues.remove(it) }
                    if (!pValues.containsKey("seq") || (pValues.get("seq") as? Int
                            ?: 0) < 0
                    ) pValues.put("seq", index)
                    if (pValues.get("ct") == "text/plain" && !pValues.containsKey("chset")) pValues.put(
                        "chset",
                        106
                    )
                    var pUri = try {
                        context.contentResolver.insert(
                            "content://mms/$mmsId/part".toUri(),
                            pValues
                        )
                    } catch (e: Exception) {
                        Log.e("DeviceManager", "Standard insert failed for part $index", e); null
                    }
                    if (pUri == null) {
                        Log.d("DeviceManager", "Attempting force insert for part $index")
                        pUri = forceInsertPart(mmsId, pValues)
                    }
                    if (pUri != null) {
                        partFiles[index]?.let { file ->
                            try {
                                context.contentResolver.openOutputStream(pUri)
                                    ?.use { o -> file.inputStream().use { i -> i.copyTo(o) } }
                                Log.d(
                                    "DeviceManager",
                                    "Restored attachment for part $index from ${file.name}"
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    "DeviceManager",
                                    "Failed to restore attachment for part $index via ContentResolver",
                                    e
                                )
                            }
                        }
                    } else {
                        Log.e("DeviceManager", "Failed to insert part $index")
                    }
                }
                addrs.forEach { aValues ->
                    aValues.remove("_id"); aValues.remove("msg_id")
                    if (!aValues.containsKey("charset")) aValues.put("charset", 106)
                    try {
                        context.contentResolver.insert(
                            "content://mms/$mmsId/addr".toUri(),
                            aValues
                        )
                    } catch (e: Exception) {
                        Log.e("DeviceManager", "Failed to insert address", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceManager", "Critical failure restoring MMS object", e)
            }
        }
        reader.endArray()
        Log.d("DeviceManager", "MMS restoration completed. Restored $count MMS.")
        onProgress("Restored $count Messages.")
    }

    private fun backupCallLogs(context: Context, jsonFile: File) {
        val cursor =
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, null)
                ?: return
        jsonFile.outputStream().use { fos ->
            val writer =
                JsonWriter(OutputStreamWriter(fos, "UTF-8")).apply { setIndent("  "); beginArray() }
            while (cursor.moveToNext()) {
                writer.beginObject()
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_STRING -> writer.name(name).value(cursor.getString(i))
                        Cursor.FIELD_TYPE_INTEGER -> writer.name(name).value(cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> writer.name(name).value(cursor.getDouble(i))
                        Cursor.FIELD_TYPE_NULL -> writer.name(name).nullValue()
                        Cursor.FIELD_TYPE_BLOB -> writer.name(name).nullValue()
                    }
                }
                writer.endObject()
            }
            writer.endArray(); writer.close()
        }
        cursor.close()
    }

    private fun restoreCallLogs(context: Context, jsonFile: File, onProgress: (String) -> Unit) {
        if (!jsonFile.exists()) return
        val validCols = getValidColumns(context, CallLog.Calls.CONTENT_URI)
        var count = 0
        try {
            jsonFile.inputStream().use { fis ->
                val reader = JsonReader(InputStreamReader(fis, "UTF-8")).apply { beginArray() }
                val batch = mutableListOf<ContentValues>()
                while (reader.hasNext()) {
                    val values = ContentValues(); reader.beginObject()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        val peek = reader.peek()
                        if (peek == JsonToken.NULL) {
                            reader.nextNull(); continue
                        }
                        if (name == "_id" || (validCols.isNotEmpty() && !validCols.contains(name))) {
                            reader.skipValue(); continue
                        }
                        when (peek) {
                            JsonToken.STRING -> values.put(name, reader.nextString())
                            JsonToken.NUMBER -> values.put(name, reader.nextLong())
                            JsonToken.BOOLEAN -> values.put(name, reader.nextBoolean())
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject(); batch.add(values)
                    if (batch.size >= 50) {
                        count += flushBatch(
                            context,
                            CallLog.Calls.CONTENT_URI,
                            batch
                        ); onProgress("Restoring Calls ($count)..."); batch.clear()
                    }
                }
                reader.endArray()
                if (batch.isNotEmpty()) count += flushBatch(
                    context,
                    CallLog.Calls.CONTENT_URI,
                    batch
                )
            }
            onProgress("Restored $count Calls.")
        } catch (e: Exception) {
            onProgress("Error: ${e.message}")
        }
    }

    @Suppress("SameParameterValue")
    private fun triggerRescan(packageName: String = "com.google.android.apps.messaging") {
        Shell.cmd("am force-stop $packageName && am force-stop com.google.android.gms").exec()
    }

    suspend fun runOperation(
        context: Context,
        isBackup: Boolean,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean,
        doWifi: Boolean,
        doWallpaper: Boolean,
        doBluetooth: Boolean,
        savedPath: String,
        updateState: (step: String, progress: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val backupDir = "$savedPath/Device"
        val cacheDir = context.cacheDir.absolutePath
        val zapdosPath = "/data/adb/Shifter/zapdos"
        updateState(
            "Initializing...",
            -1
        ); threadIdCache.clear(); Shell.cmd("mkdir -p \"$backupDir\"").exec()
        val selectedItems = mutableListOf<String>()
        if (doSms) selectedItems.add("Messages")
        if (doCall) selectedItems.add("Calls")
        if (doContacts) selectedItems.add("Contacts")
        if (doWifi) selectedItems.add("Wi-Fi")
        if (doWallpaper) selectedItems.add("Wallpaper")
        if (doBluetooth) selectedItems.add("Bluetooth")
        if (selectedItems.isEmpty()) return@withContext
        var currentItemIndex = 0
        val slice = 100f / selectedItems.size
        fun notify(step: String, internalProgress: Int) {
            updateState(
                step,
                ((currentItemIndex * slice) + (internalProgress * slice / 100f)).toInt()
                    .coerceIn(0, 99)
            )
        }

        val perms = mutableListOf<String>()
        if (doSms) perms.addAll(
            listOf(
                "READ_SMS",
                "WRITE_SMS",
                "RECEIVE_SMS",
                "RECEIVE_MMS",
                "READ_PHONE_STATE"
            )
        )
        if (doCall) perms.addAll(listOf("READ_CALL_LOG", "WRITE_CALL_LOG"))
        if (doContacts) perms.addAll(listOf("READ_CONTACTS", "WRITE_CONTACTS"))
        perms.forEach { Shell.cmd("pm grant $pkg android.permission.$it").exec() }
        if (doSms) Shell.cmd("appops set $pkg WRITE_SMS allow").exec()
        if (doCall) Shell.cmd("appops set $pkg WRITE_CALL_LOG allow").exec()
        try {
            if (isBackup) {
                if (doSms) {
                    notify("Backing up Messages...", 0)
                    val msgJ = File(context.cacheDir, "messages.json")
                    val partsD = File(context.cacheDir, "mms_parts")
                    partsD.mkdirs()
                    msgJ.outputStream().use { fos ->
                        val writer = JsonWriter(
                            OutputStreamWriter(
                                fos,
                                "UTF-8"
                            )
                        ).apply { setIndent("  "); beginObject() }
                        backupSms(context, writer)
                        backupMms(context, writer, partsD)
                        writer.endObject(); writer.close()
                    }
                    notify(
                        "Compressing Messages...",
                        80
                    ); Shell.cmd("cd \"$cacheDir\" && tar -cf - messages.json mms_parts 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Messages.shift\"")
                        .exec()
                    msgJ.delete(); partsD.deleteRecursively(); currentItemIndex++
                }
                if (doCall) {
                    notify("Backing up Calls...", 0)
                    val callsJ = File(context.cacheDir, "calls.json")
                    backupCallLogs(context, callsJ); notify(
                        "Compressing Calls...",
                        80
                    ); Shell.cmd("cd \"$cacheDir\" && tar -cf - calls.json 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/CallLogs.shift\"")
                        .exec()
                    callsJ.delete(); currentItemIndex++
                }
                if (doContacts) {
                    notify("Backing up Contacts...", 0)
                    val vcf = File(context.cacheDir, "Contacts.vcf")
                    context.contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        null,
                        null,
                        null,
                        null
                    )?.use {
                        if (it.count > 0) {
                            val keys = StringBuilder(); while (it.moveToNext()) keys.append(
                                it.getString(
                                    it.getColumnIndexOrThrow(
                                        ContactsContract.Contacts.LOOKUP_KEY
                                    )
                                )
                            ).append(":"); context.contentResolver.openInputStream(
                                Uri.withAppendedPath(
                                    ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
                                    Uri.encode(keys.toString())
                                )
                            )?.use { i -> vcf.outputStream().use { o -> i.copyTo(o) } }
                        }
                    }
                    if (vcf.exists()) {
                        notify(
                            "Compressing Contacts...",
                            80
                        ); Shell.cmd("cd \"$cacheDir\" && tar -cf - Contacts.vcf 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Contacts.shift\"")
                            .exec(); vcf.delete()
                    }
                    currentItemIndex++
                }
                if (doWifi) {
                    notify(
                        "Backing up Wi-Fi...",
                        0
                    ); Shell.cmd("mkdir -p \"$cacheDir/Wi-Fi\" && sh /data/adb/Shifter/ROM-Shifter.sh --backup-wifi \"$cacheDir/Wi-Fi\"")
                        .exec(); notify(
                        "Compressing Wi-Fi...",
                        80
                    ); Shell.cmd("cd \"$cacheDir\" && tar -cf - Wi-Fi 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Wi-Fi.shift\"")
                        .exec(); Shell.cmd("rm -rf \"$cacheDir/Wi-Fi\""); currentItemIndex++
                }
                if (doWallpaper) {
                    notify(
                        "Backing up Wallpaper...",
                        0
                    ); Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --backup-wallpaper \"$cacheDir/Wallpaper\"")
                        .exec(); notify(
                        "Compressing Wallpaper...",
                        80
                    ); Shell.cmd("cd \"$cacheDir\" && tar -cf - Wallpaper 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Wallpaper.shift\"")
                        .exec(); Shell.cmd("rm -rf \"$cacheDir/Wallpaper\""); currentItemIndex++
                }
                if (doBluetooth) {
                    notify(
                        "Backing up Bluetooth...",
                        0
                    ); Shell.cmd("mkdir -p \"$cacheDir/Bluetooth\" && sh /data/adb/Shifter/ROM-Shifter.sh --backup-bt \"$cacheDir/Bluetooth\"")
                        .exec(); notify(
                        "Compressing Bluetooth...",
                        80
                    ); Shell.cmd("cd \"$cacheDir\" && tar -cf - Bluetooth 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Bluetooth.shift\"")
                        .exec(); Shell.cmd("rm -rf \"$cacheDir/Bluetooth\""); currentItemIndex++
                }
            } else {
                if (doSms) {
                    if (!Shell.cmd("[ -d /data/user/0/com.android.providers.telephony/databases ]")
                            .exec().isSuccess
                    ) ensureTelephonyDirs(context)
                    notify(
                        "Extracting Messages...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Messages.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec(); Shell.cmd("""chmod -R 777 "$cacheDir"""").exec()
                    val msgJ = File(context.cacheDir, "messages.json")
                    if (msgJ.exists()) {
                        msgJ.inputStream().use { fis ->
                            val reader =
                                JsonReader(InputStreamReader(fis, "UTF-8")).apply { beginObject() }
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "sms" -> restoreSms(context, reader) { msg -> notify(msg, 10) }
                                    "mms" -> restoreMms(
                                        context,
                                        reader,
                                        File(context.cacheDir, "mms_parts")
                                    ) { msg -> notify(msg, 40) }

                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject(); reader.close()
                        }
                    }
                    triggerRescan("com.google.android.apps.messaging")
                    msgJ.delete(); File(
                        context.cacheDir,
                        "mms_parts"
                    ).deleteRecursively(); currentItemIndex++
                }
                if (doCall) {
                    notify(
                        "Extracting Calls...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/CallLogs.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec(); Shell.cmd("chmod -R 777 \"$cacheDir\"").exec()
                    val callsJ =
                        File(context.cacheDir, "calls.json"); if (callsJ.exists()) restoreCallLogs(
                        context,
                        callsJ
                    ) { msg -> notify(msg, 50) }
                    callsJ.delete(); currentItemIndex++
                }
                if (doContacts) {
                    notify(
                        "Extracting Contacts...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Contacts.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec()
                    val vcf = File(context.cacheDir, "Contacts.vcf")
                    Shell.cmd("chmod 666 \"${vcf.absolutePath}\"").exec(); if (vcf.exists()) {
                        importVcf(context, vcf) { prog ->
                            notify(
                                "Importing Contacts...",
                                prog
                            )
                        }; vcf.delete()
                    }
                    currentItemIndex++
                }
                if (doWifi) {
                    notify(
                        "Extracting Wi-Fi...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Wi-Fi.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec(); notify(
                        "Applying Wi-Fi...",
                        50
                    ); Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-wifi \"$cacheDir/Wi-Fi\"")
                        .exec(); Shell.cmd("rm -rf \"$cacheDir/Wi-Fi\""); currentItemIndex++
                }
                if (doWallpaper) {
                    notify(
                        "Extracting Wallpaper...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Wallpaper.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec(); notify(
                        "Applying Wallpaper...",
                        50
                    ); Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-wallpaper \"$cacheDir/Wallpaper\"")
                        .exec()
                    try {
                        val wm = WallpaperManager.getInstance(context)
                        val wp = File(cacheDir, "Wallpaper/wallpaper"); if (wp.exists()) {
                            Shell.cmd("chmod 666 \"${wp.absolutePath}\"")
                                .exec(); FileInputStream(wp).use {
                                wm.setStream(
                                    it,
                                    null,
                                    true,
                                    WallpaperManager.FLAG_SYSTEM
                                )
                            }
                        }
                        val lk = File(cacheDir, "Wallpaper/wallpaper_lock")
                        val lko = File(cacheDir, "Wallpaper/wallpaper_lock_orig")
                        val fl =
                            if (lk.exists()) lk else if (lko.exists()) lko else null; if (fl != null) {
                            Shell.cmd("chmod 666 \"${fl.absolutePath}\"")
                                .exec(); FileInputStream(fl).use {
                                wm.setStream(
                                    it,
                                    null,
                                    true,
                                    WallpaperManager.FLAG_LOCK
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                    Shell.cmd("rm -rf \"$cacheDir/Wallpaper\""); currentItemIndex++
                }
                if (doBluetooth) {
                    notify(
                        "Extracting Bluetooth...",
                        0
                    ); Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Bluetooth.shift\" | tar -xf - -C \"$cacheDir\"")
                        .exec(); notify(
                        "Applying Bluetooth...",
                        50
                    ); Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-bt \"$cacheDir/Bluetooth\"")
                        .exec(); Shell.cmd("rm -rf \"$cacheDir/Bluetooth\""); currentItemIndex++
                }
            }
        } finally {
        }
    }
}
