package build.bytes.romshifter.utils

import android.app.WallpaperManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
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
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object NativeManager {
    fun getAvailableBackups(savedPath: String): Set<String> {
        val nativeDir = "$savedPath/Native"
        return Shell.cmd("ls \"$nativeDir\"").exec().out.toSet()
    }

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
        updateProgress: (Int) -> Unit
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
                if (index % 50 == 0 && totalLines > 0) updateProgress(index * 100 / totalLines)
            }
            if (ops.isNotEmpty()) context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY,
                ops
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreFromJson(
        context: Context,
        jsonFile: File,
        contentUri: Uri,
        stepName: String,
        startProgress: Int,
        progressWeight: Int,
        notify: (String, Int) -> Unit
    ) {
        if (!jsonFile.exists()) return
        var total = 0
        try {
            val countReader = JsonReader(InputStreamReader(FileInputStream(jsonFile), "UTF-8"))
            countReader.beginArray()
            while (countReader.hasNext()) {
                countReader.skipValue()
                total++
            }
            countReader.close()
        } catch (_: Exception) {
        }

        val reader = JsonReader(InputStreamReader(FileInputStream(jsonFile), "UTF-8"))
        reader.beginArray()
        var i = 0
        while (reader.hasNext()) {
            val values = ContentValues()
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                } else {
                    val value = reader.nextString()
                    if (name != "_id") values.put(name, value)
                }
            }
            reader.endObject()
            try {
                context.contentResolver.insert(contentUri, values)
            } catch (_: Exception) {
            }
            if (i++ % 100 == 0) {
                val currentProgress =
                    if (total > 0) (i * progressWeight / total) else (i * progressWeight / 2000)
                notify(
                    "Restoring $stepName...",
                    startProgress + currentProgress.coerceAtMost(progressWeight)
                )
            }
        }
        reader.endArray()
        reader.close()
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
        val backupDir = "$savedPath/Native"
        val cacheDir = context.cacheDir.absolutePath
        val zapdosPath = "/data/adb/Shifter/zapdos"

        Shell.cmd("mkdir -p \"$backupDir\"").exec()

        val selectedItems = mutableListOf<String>()
        if (doSms) selectedItems.add("SMS")
        if (doCall) selectedItems.add("Calls")
        if (doContacts) selectedItems.add("Contacts")
        if (doWifi) selectedItems.add("WiFi")
        if (doWallpaper) selectedItems.add("Wallpaper")
        if (doBluetooth) selectedItems.add("Bluetooth")

        val totalItems = selectedItems.size
        if (totalItems == 0) return@withContext
        var currentItemIndex = 0
        val slice = 100f / totalItems

        fun notify(step: String, internalProgress: Int) {
            val totalProgress = (currentItemIndex * slice) + (internalProgress * slice / 100f)
            updateState(step, totalProgress.toInt().coerceIn(0, 99))
        }

        val permsToGrant = mutableListOf<String>()
        if (doSms) permsToGrant.addAll(listOf("READ_SMS", "WRITE_SMS"))
        if (doCall) permsToGrant.addAll(listOf("READ_CALL_LOG", "WRITE_CALL_LOG"))
        if (doContacts) permsToGrant.addAll(listOf("READ_CONTACTS", "WRITE_CONTACTS"))
        val grantCmds = permsToGrant.map { "pm grant $pkg android.permission.$it" }.toTypedArray()
        if (grantCmds.isNotEmpty()) Shell.cmd(*grantCmds).exec()

        if (isBackup) {
            if (doSms) {
                notify("Backing up SMS, MMS & RCS...", 0)
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
                        if (i++ % 100 == 0) notify(
                            "Backing up SMS ($i/$count)...",
                            (i * 50) / count.coerceAtLeast(1)
                        )
                    }
                }
                writer.endArray()
                writer.close()
                notify("Backing up Advanced MMS & RCS...", 60)
                Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --backup-msgs \"$cacheDir\"").exec()
                notify("Compressing Messages...", 80)
                Shell.cmd("cd \"$cacheDir\" && tar -cf - SMS_DB.json Advanced_Msgs 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Messages.shift\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/SMS_DB.json\" \"$cacheDir/Advanced_Msgs\"").exec()
                currentItemIndex++
            }
            if (doCall) {
                notify("Backing up Call Logs...", 0)
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
                        if (i++ % 100 == 0) notify(
                            "Backing up Call Logs ($i/$count)...",
                            (i * 80) / count.coerceAtLeast(1)
                        )
                    }
                }
                writer.endArray()
                writer.close()
                notify("Compressing Call Logs...", 90)
                Shell.cmd("cd \"$cacheDir\" && tar -cf - CallLog_DB.json 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/CallLogs.shift\"")
                    .exec()
                callFile.delete()
                currentItemIndex++
            }
            if (doContacts) {
                notify("Backing up Contacts (vCard)...", 0)
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
                    notify("Compressing Contacts...", 80)
                    Shell.cmd("cd \"$cacheDir\" && tar -cf - Contacts.vcf 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Contacts.shift\"")
                        .exec()
                    vcfFile.delete()
                }
                currentItemIndex++
            }
            if (doWifi) {
                notify("Backing up WiFi Configs...", 0)
                Shell.cmd("mkdir -p \"$cacheDir/Wifi\" && sh /data/adb/Shifter/ROM-Shifter.sh --backup-wifi \"$cacheDir/Wifi\"")
                    .exec()
                notify("Compressing WiFi...", 50)
                Shell.cmd("cd \"$cacheDir\" && tar -cf - Wifi 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Wifi.shift\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/Wifi\"").exec()
                currentItemIndex++
            }
            if (doWallpaper) {
                notify("Backing up Wallpaper...", 0)
                Shell.cmd("mkdir -p \"$cacheDir/Wallpaper\" && sh /data/adb/Shifter/ROM-Shifter.sh --backup-wallpaper \"$cacheDir/Wallpaper\"")
                    .exec()
                notify("Compressing Wallpaper...", 50)
                Shell.cmd("cd \"$cacheDir\" && tar -cf - Wallpaper 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Wallpaper.shift\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/Wallpaper\"").exec()
                currentItemIndex++
            }
            if (doBluetooth) {
                notify("Backing up Bluetooth Pairings...", 0)
                Shell.cmd("mkdir -p \"$cacheDir/Bluetooth\" && sh /data/adb/Shifter/ROM-Shifter.sh --backup-bt \"$cacheDir/Bluetooth\"")
                    .exec()
                notify("Compressing Bluetooth...", 50)
                Shell.cmd("cd \"$cacheDir\" && tar -cf - Bluetooth 2>/dev/null | \"$zapdosPath\" -1 -f -q -o \"$backupDir/Bluetooth.shift\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/Bluetooth\"").exec()
                currentItemIndex++
            }
        } else {
            if (doSms) {
                notify("Extracting Messages...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Messages.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                val hasRawDbs =
                    Shell.cmd("[ -d \"$cacheDir/Advanced_Msgs/Telephony\" ] && echo YES")
                        .exec().out.joinToString("").trim() == "YES"
                if (hasRawDbs) {
                    notify("Injecting Raw MMS & RCS Databases...", 30)
                    Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-msgs \"$cacheDir\"")
                        .exec()
                } else {
                    notify("Restoring SMS from JSON...", 30)
                    val currentSmsApp = Shell.cmd("cmd role get-role-holders android.app.role.SMS")
                        .exec().out.joinToString("").trim()
                    Shell.cmd("cmd role add-role-holder android.app.role.SMS $pkg").exec()
                    Shell.cmd("appops set $pkg WRITE_SMS allow").exec()
                    val tempSms = File(context.cacheDir, "SMS_DB.json")
                    Shell.cmd("chmod 666 \"${tempSms.absolutePath}\"").exec()
                    restoreFromJson(
                        context,
                        tempSms,
                        "content://sms".toUri(),
                        "SMS",
                        30,
                        60,
                        ::notify
                    )
                    tempSms.delete()
                    if (currentSmsApp.isNotEmpty()) {
                        Shell.cmd("cmd role add-role-holder android.app.role.SMS $currentSmsApp")
                            .exec()
                    }
                }
                Shell.cmd("rm -rf \"$cacheDir/Advanced_Msgs\" \"$cacheDir/SMS_DB.json\"").exec()
                currentItemIndex++
            }
            if (doCall) {
                notify("Extracting Call Logs...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/CallLogs.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                Shell.cmd("appops set $pkg WRITE_CALL_LOG allow").exec()
                val tempCall = File(context.cacheDir, "CallLog_DB.json")
                Shell.cmd("chmod 666 \"${tempCall.absolutePath}\"").exec()
                restoreFromJson(
                    context,
                    tempCall,
                    android.provider.CallLog.Calls.CONTENT_URI,
                    "Call Logs",
                    20,
                    70,
                    ::notify
                )
                tempCall.delete()
                currentItemIndex++
            }
            if (doContacts) {
                notify("Extracting Contacts (vCard)...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Contacts.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                val tempVcf = File(context.cacheDir, "Contacts.vcf")
                Shell.cmd("chmod 666 \"${tempVcf.absolutePath}\"").exec()
                if (tempVcf.exists()) {
                    importVcf(context, tempVcf) { prog -> notify("Importing Contacts...", prog) }
                    tempVcf.delete()
                }
                currentItemIndex++
            }
            if (doWifi) {
                notify("Extracting WiFi Configs...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Wifi.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                notify("Applying WiFi Configs...", 50)
                Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-wifi \"$cacheDir/Wifi\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/Wifi\"").exec()
                currentItemIndex++
            }
            if (doWallpaper) {
                notify("Extracting Wallpaper...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Wallpaper.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                notify("Applying Wallpaper Files...", 40)
                Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-wallpaper \"$cacheDir/Wallpaper\"")
                    .exec()
                notify("Applying Wallpaper UI...", 80)
                try {
                    val wm = WallpaperManager.getInstance(context)
                    val wpFile = File(cacheDir, "Wallpaper/wallpaper")
                    if (wpFile.exists()) {
                        Shell.cmd("chmod 666 \"${wpFile.absolutePath}\"").exec()
                        FileInputStream(wpFile).use {
                            wm.setStream(
                                it,
                                null,
                                true,
                                WallpaperManager.FLAG_SYSTEM
                            )
                        }
                    }
                    val lockFile = File(cacheDir, "Wallpaper/wallpaper_lock")
                    val lockFileOrig = File(cacheDir, "Wallpaper/wallpaper_lock_orig")
                    val finalLock =
                        if (lockFile.exists()) lockFile else if (lockFileOrig.exists()) lockFileOrig else null
                    if (finalLock != null) {
                        Shell.cmd("chmod 666 \"${finalLock.absolutePath}\"").exec()
                        FileInputStream(finalLock).use {
                            wm.setStream(
                                it,
                                null,
                                true,
                                WallpaperManager.FLAG_LOCK
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NativeManager", "Failed to apply wallpaper via API", e)
                }
                Shell.cmd("rm -rf \"$cacheDir/Wallpaper\"").exec()
                currentItemIndex++
            }
            if (doBluetooth) {
                notify("Extracting Bluetooth Pairings...", 0)
                Shell.cmd("\"$zapdosPath\" -d -q -c \"$backupDir/Bluetooth.shift\" | tar -xf - -C \"$cacheDir\" 2>/dev/null")
                    .exec()
                notify("Applying Bluetooth Pairings...", 50)
                Shell.cmd("sh /data/adb/Shifter/ROM-Shifter.sh --restore-bt \"$cacheDir/Bluetooth\"")
                    .exec()
                Shell.cmd("rm -rf \"$cacheDir/Bluetooth\"").exec()
                currentItemIndex++
            }
        }
    }
}
