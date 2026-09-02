package build.bytes.romshifter.utils

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.provider.Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != android.provider.Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
    }
}

class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
