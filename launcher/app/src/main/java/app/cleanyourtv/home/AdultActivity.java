package app.cleanyourtv.home;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Parent home. The shelf is TizenTube only. HDMI and settings stay available. There is no countdown. */
public class AdultActivity extends TvActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomeBoard board;
    private Runnable tick;

    @Override
    public void onBackPressed() {
        if (board != null && board.panelOpen()) board.hidePanel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        Store store = Store.get(this);
        Store.Session session = store.session(System.currentTimeMillis());
        if (!session.active || !session.adult) {
            startActivity(new Intent(this, PickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            return;
        }
        board = HomeBoard.create(this);
        board.tickClock(true);
        board.pill("訊號源", this::inputs);
        board.pill("設定", this::settingsMenu);
        board.setTiles(Devices.shown(this), new ArrayList<>());
        board.bind(true);
        setContentView(board.root);
        WeatherBoard.attach(this, board);
        tick = () -> {
            if (isFinishing() || board == null) return;
            board.tickClock(true);
            handler.postDelayed(tick, 1000);
        };
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(tick, 1000);
    }

    private void inputs() {
        List<HomeBoard.Choice> rows = new ArrayList<>();
        for (Devices.Source source : Devices.sources(this)) {
            String label = source.detail == null || source.detail.isEmpty() ? source.name : source.name + "  " + source.detail;
            rows.add(new HomeBoard.Choice(label, !source.connected, () -> Devices.open(this, source.uri)));
        }
        if (rows.isEmpty()) rows.add(new HomeBoard.Choice("這台沒有回報訊號源", true, null));
        board.showPanel("訊號源", rows);
    }

    private void settingsMenu() {
        List<HomeBoard.Choice> rows = new ArrayList<>();
        rows.add(new HomeBoard.Choice("修改密碼", false, () -> startActivity(new Intent(this, PinActivity.class)
                .putExtra("id", "adult").putExtra("change", true))));
        rows.add(new HomeBoard.Choice("電視設定", false, () -> Devices.openSettings(this)));
        rows.add(new HomeBoard.Choice("Wi-Fi", false, () -> Devices.openWifi(this)));
        rows.add(new HomeBoard.Choice("家長設定", false, () -> startActivity(new Intent(this, SettingsActivity.class))));
        rows.add(new HomeBoard.Choice("切換帳號", false, () -> {
            Store.get(this).end("");
            startActivity(new Intent(this, PickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        }));
        board.showPanel("設定", rows);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
