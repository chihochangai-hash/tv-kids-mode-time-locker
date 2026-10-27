package app.tvkids.timelocker;

import android.app.Activity;
import android.content.Intent;
import android.os.PowerManager;
import android.view.KeyEvent;

/** Back stays inside this launcher. Home is not finished, because the system would just start it again. */
public class TvActivity extends Activity {
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP) onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        WatchService.ensure(this);
        if (leaveAfterPower()) return;
        Store.get(this).hold(System.currentTimeMillis());
    }

    @Override
    protected void onPause() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && !power.isInteractive()) Store.get(this).powerOff();
        super.onPause();
    }

    /** The account picker stays up. Every other page closes after the television was powered off. */
    private boolean leaveAfterPower() {
        Store store = Store.get(this);
        if (this instanceof PickerActivity) {
            store.consumePowerOff();
            return false;
        }
        if (!store.consumePowerOff()) return false;
        Intent picker = new Intent(this, PickerActivity.class);
        picker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(picker);
        finish();
        return true;
    }
}
