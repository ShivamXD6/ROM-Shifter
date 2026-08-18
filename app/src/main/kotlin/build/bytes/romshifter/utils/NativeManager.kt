package build.bytes.romshifter.utils

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.core.net.toUri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object NativeManager {

    private fun decodeQuotedPrintable(input: String): String {
        return try {
            val out = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '=') {
                    val hex = input.substring(i + 1, i + 3)
                    out.write(hex.toInt(16))
                    i += 3
                } else {
                    out.write(c.code)
                    i++
                }
            }
            out.toString("UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    private fun importVcf(
        context: Context,
        vcfFile: File,
        updateState: (step: String, progress: Int) -> Unit
    ) {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            val unfoldedLines = mutableListOf<String>()
            var currentUnfoldedLine: StringBuilder? = null

            vcfFile.forEachLine { line ->
                if (line.startsWith(" ") || line.startsWith("\t")) {
                    currentUnfoldedLine?.append(line.substring(1))
                } else {
                    currentUnfoldedLine?.let { unfoldedLines.add(it.toString()) }
                    currentUnfoldedLine = StringBuilder(line)
                }
            }
            currentUnfoldedLine?.let { unfoldedLines.add(it.toString()) }

            var contactName: String? = null
            val phoneNumbers = mutableListOf<String>()
            var photoBytes: ByteArray? = null

            val totalLines = unfoldedLines.size
            unfoldedLines.forEachIndexed { index, line ->
                when {
                    line.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                        contactName = null
                        phoneNumbers.clear()
                        photoBytes = null
                    }

                    (line.startsWith("FN:", ignoreCase = true) || line.startsWith(
                        "FN;",
                        ignoreCase = true
                    )) -> {
                        val value = line.substringAfter(":").trim()
                        contactName =
                            if (line.contains("ENCODING=QUOTED-PRINTABLE", ignoreCase = true)) {
                                decodeQuotedPrintable(value)
                            } else {
                                value
                            }
                    }

                    (line.startsWith("N:", ignoreCase = true) || line.startsWith(
                        "N;",
                        ignoreCase = true
                    )) && contactName == null -> {
                        val value = line.substringAfter(":").trim()
                        val decoded =
                            if (line.contains("ENCODING=QUOTED-PRINTABLE", ignoreCase = true)) {
                                decodeQuotedPrintable(value)
                            } else {
                                value
                            }
                        contactName =
                            decoded.split(";").filter { it.isNotBlank() }.joinToString(" ")
                    }

                    line.startsWith("TEL", ignoreCase = true) -> {
                        val number = line.substringAfter(":").trim()
                        if (number.isNotEmpty()) phoneNumbers.add(number)
                    }

                    line.startsWith("PHOTO", ignoreCase = true) -> {
                        val base64Data = line.substringAfter(":").trim()
                        try {
                            photoBytes =
                                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        } catch (_: Exception) {
                        }
                    }

                    line.startsWith("END:VCARD", ignoreCase = true) -> {
                        if (!contactName.isNullOrEmpty() || phoneNumbers.isNotEmpty()) {
                            val rawContactIndex = ops.size
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
                                        rawContactIndex
                                    )
                                    .withValue(
                                        ContactsContract.Data.MIMETYPE,
                                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                                    )
                                    .withValue(
                                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                                        contactName ?: "Unknown"
                                    )
                                    .build()
                            )

                            for (number in phoneNumbers) {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(
                                            ContactsContract.Data.RAW_CONTACT_ID,
                                            rawContactIndex
                                        )
                                        .withValue(
                                            ContactsContract.Data.MIMETYPE,
                                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                                        )
                                        .withValue(
                                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                                            number
                                        )
                                        .withValue(
                                            ContactsContract.CommonDataKinds.Phone.TYPE,
                                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                        )
                                        .build()
                                )
                            }

                            photoBytes?.let {
                                ops.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(
                                            ContactsContract.Data.RAW_CONTACT_ID,
                                            rawContactIndex
                                        )
                                        .withValue(
                                            ContactsContract.Data.MIMETYPE,
                                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                                        )
                                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, it)
                                        .build()
                                )
                            }
                        }

                        if (ops.size > 100) {
                            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                            ops.clear()
                        }
                    }
                }
                if (index % 50 == 0 && totalLines > 0) updateState(
                    "Importing contacts...",
                    90 + (index * 10 / totalLines).coerceAtMost(9)
                )
            }
            if (ops.isNotEmpty()) context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY,
                ops
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun runOperation(
        context: Context,
        isBackup: Boolean,
        doSms: Boolean,
        doCall: Boolean,
        doContacts: Boolean,
        savedPath: String,
        updateState: (step: String, progress: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val backupDir = "$savedPath/Native"
        val cacheDir = context.cacheDir.absolutePath
        val zapdosPath = "/data/adb/Shifter/zapdos"

        Shell.cmd("su -c 'mkdir -p \"$backupDir\"'").exec()

        val permsToGrant = mutableListOf<String>()
        if (doSms) permsToGrant.addAll(listOf("READ_SMS", "WRITE_SMS"))
        if (doCall) permsToGrant.addAll(listOf("READ_CALL_LOG", "WRITE_CALL_LOG"))
        if (doContacts) permsToGrant.addAll(listOf("READ_CONTACTS", "WRITE_CONTACTS"))

        val grantCmds = permsToGrant.map { "pm grant $pkg android.permission.$it" }.toTypedArray()
        if (grantCmds.isNotEmpty()) Shell.cmd(*grantCmds).exec()

        if (isBackup) {
            if (doSms) {
                updateState("Backing up SMS, MMS & RCS...", 0)
                val smsFile = File(context.cacheDir, "SMS_DB.json")
                val writer = JsonWriter(OutputStreamWriter(FileOutputStream(smsFile), "UTF-8"))
                writer.beginArray()
                context.contentResolver.query("content://sms".toUri(), null, null, null, null)?.use { cursor ->
                    val count = cursor.count
                    var i = 0
                    while (cursor.moveToNext()) {
                        writer.beginObject()
                        for (c in 0 until cursor.columnCount) {
                            val name = cursor.getColumnName(c)
                            val value = cursor.getString(c)
                            if (value != null) writer.name(name).value(value)
                        }
                        writer.endObject()
                        if (i++ % 100 == 0) updateState("Backing up basic SMS...", (i * 20) / count)
                    }
                }
                writer.endArray()
                writer.close()

                updateState("Backing up Advanced MMS & RCS...", 30)
                Shell.cmd("su -mm -c 'sh /data/adb/Shifter/ROM-Shifter.sh --backup-msgs \"$cacheDir\"'")
                    .exec()

                updateState("Compressing Messages...", 40)
                Shell.cmd("su -mm -c 'cd \"$cacheDir\" && tar -cf - SMS_DB.json Advanced_Msgs 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Messages.shift\"'").exec()
                Shell.cmd("su -mm -c 'rm -rf \"$cacheDir/SMS_DB.json\" \"$cacheDir/Advanced_Msgs\"'").exec()
            }
            if (doCall) {
                updateState("Backing up Call Logs...", 50)
                val callFile = File(context.cacheDir, "CallLog_DB.json")
                val writer = JsonWriter(OutputStreamWriter(FileOutputStream(callFile), "UTF-8"))
                writer.beginArray()
                context.contentResolver.query(android.provider.CallLog.Calls.CONTENT_URI, null, null, null, null)?.use { cursor ->
                    val count = cursor.count
                    var i = 0
                    while (cursor.moveToNext()) {
                        writer.beginObject()
                        for (c in 0 until cursor.columnCount) {
                            val name = cursor.getColumnName(c)
                            val value = cursor.getString(c)
                            if (value != null) writer.name(name).value(value)
                        }
                        writer.endObject()
                        if (i++ % 100 == 0) updateState("Backing up Call Logs...", 50 + ((i * 30) / count))
                    }
                }
                writer.endArray()
                writer.close()

                updateState("Compressing Call Logs...", 80)
                Shell.cmd("su -mm -c 'cd \"$cacheDir\" && tar -cf - CallLog_DB.json 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/CallLogs.shift\"'").exec()
                callFile.delete()
            }
            if (doContacts) {
                updateState("Backing up Contacts (vCard)...", 90)
                val vcfFile = File(context.cacheDir, "Contacts.vcf")
                context.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)?.use { cursor ->
                    if (cursor.count > 0) {
                        val lookupKeys = java.lang.StringBuilder()
                        while (cursor.moveToNext()) {
                            val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                            lookupKeys.append(lookupKey).append(":")
                        }
                        val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(lookupKeys.toString()))
                        val input = context.contentResolver.openInputStream(lookupUri)
                        val output = FileOutputStream(vcfFile)
                        input?.copyTo(output)
                        input?.close(); output.close()
                    }
                }
                if (vcfFile.exists()) {
                    updateState("Compressing Contacts...", 95)
                    Shell.cmd("su -mm -c 'cd \"$cacheDir\" && tar -cf - Contacts.vcf 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Contacts.shift\"'").exec()
                    vcfFile.delete()
                }
            }
        } else {
            if (doSms) {
                updateState("Extracting Messages...", 0)
                Shell.cmd("su -mm -c '\"$zapdosPath\" -d -q -c \"$backupDir/Messages.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null'").exec()

                val hasRawDbs = Shell.cmd("su -c '[ -d \"$cacheDir/Advanced_Msgs/Telephony\" ] && echo YES'").exec().out.joinToString("").trim() == "YES"

                if (hasRawDbs) {
                    updateState("Injecting Raw MMS & RCS Databases...", 10)
                    Shell.cmd("su -mm -c 'sh /data/adb/Shifter/ROM-Shifter.sh --restore-msgs \"$cacheDir\"'")
                        .exec()
                } else {
                    updateState("Restoring SMS from JSON...", 10)
                    val currentSmsApp = Shell.cmd("su -c 'cmd role get-role-holders android.app.role.SMS'").exec().out.joinToString("").trim()
                    Shell.cmd("su -c 'cmd role add-role-holder android.app.role.SMS $pkg'").exec()
                    Shell.cmd("su -c 'appops set $pkg WRITE_SMS allow'").exec()

                    val tempSms = File(context.cacheDir, "SMS_DB.json")
                    Shell.cmd("su -c 'chmod 666 \"${tempSms.absolutePath}\"'").exec()

                    if (tempSms.exists()) {
                        val reader = JsonReader(InputStreamReader(FileInputStream(tempSms), "UTF-8"))
                        reader.beginArray()
                        var i = 0
                        while (reader.hasNext()) {
                            val values = ContentValues()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val name = reader.nextName()
                                if (reader.peek() == JsonToken.NULL) { reader.nextNull() }
                                else { val value = reader.nextString(); if (name != "_id") values.put(name, value) }
                            }
                            reader.endObject()
                            try { context.contentResolver.insert("content://sms".toUri(), values) } catch (_: Exception) {}
                            if (i++ % 100 == 0) updateState("Restoring SMS...", (i * 40) / 1000)
                        }
                        reader.endArray()
                        reader.close()
                        tempSms.delete()
                    }

                    if (currentSmsApp.isNotEmpty()) {
                        Shell.cmd("su -c 'cmd role add-role-holder android.app.role.SMS $currentSmsApp'").exec()
                    }
                }
                Shell.cmd("su -mm -c 'rm -rf \"$cacheDir/Advanced_Msgs\" \"$cacheDir/SMS_DB.json\"'").exec()
            }
            if (doCall) {
                updateState("Extracting Call Logs...", 50)
                Shell.cmd("su -mm -c '\"$zapdosPath\" -d -q -c \"$backupDir/CallLogs.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null'").exec()

                Shell.cmd("su -c 'appops set $pkg WRITE_CALL_LOG allow'").exec()
                val tempCall = File(context.cacheDir, "CallLog_DB.json")
                Shell.cmd("su -c 'chmod 666 \"${tempCall.absolutePath}\"'").exec()

                if (tempCall.exists()) {
                    val reader = JsonReader(InputStreamReader(FileInputStream(tempCall), "UTF-8"))
                    reader.beginArray()
                    var i = 0
                    while (reader.hasNext()) {
                        val values = ContentValues()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            if (reader.peek() == JsonToken.NULL) { reader.nextNull() }
                            else { val value = reader.nextString(); if (name != "_id") values.put(name, value) }
                        }
                        reader.endObject()
                        try { context.contentResolver.insert(android.provider.CallLog.Calls.CONTENT_URI, values) } catch (_: Exception) {}
                        if (i++ % 100 == 0) updateState("Restoring Call Logs...", 50 + ((i * 40) / 1000))
                    }
                    reader.endArray()
                    reader.close()
                    tempCall.delete()
                }
            }
            if (doContacts) {
                updateState("Extracting Contacts (vCard)...", 90)
                Shell.cmd("su -mm -c '\"$zapdosPath\" -d -q -c \"$backupDir/Contacts.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null'").exec()

                val tempVcf = File(context.cacheDir, "Contacts.vcf")
                Shell.cmd("su -c 'chmod 666 \"${tempVcf.absolutePath}\"'").exec()

                if (tempVcf.exists()) {
                    importVcf(context, tempVcf, updateState)
                    tempVcf.delete()
                }
            }
        }
    }
}