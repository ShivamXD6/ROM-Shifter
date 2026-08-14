package build.bytes.romshifter.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

        permsToGrant.forEach { Shell.cmd("su -c 'pm grant $pkg android.permission.$it'").exec() }

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
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.fromFile(tempVcf), "text/x-vcard")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
        }
    }
}