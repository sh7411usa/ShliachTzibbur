package com.sh7411usa.shliachtzibbur.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sh7411usa.shliachtzibbur.ShliachTzibburApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-establishes background delivery after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        val container = (context.applicationContext as ShliachTzibburApp).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val signedIn = container.sessionStore.session.first() != null
                val serviceEnabled = container.settingsStore.settings.first().syncServiceEnabled
                container.syncController.apply(signedIn, serviceEnabled)
            } finally {
                pending.finish()
            }
        }
    }
}
