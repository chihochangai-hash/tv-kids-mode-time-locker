package app.tvkids.timelocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
            Store.get(context).powerOff();
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            WatchService.ensure(context);
        }
    }
}
