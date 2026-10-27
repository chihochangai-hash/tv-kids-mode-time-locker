package app.cleanyourtv.home;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Child home. The countdown stays frozen on this page and runs only after an app or input opens. */
public class KidsActivity extends TvActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
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
        store = Store.get(this);
        long now = System.currentTimeMillis();
        if (store.lockIfClosed(now)) {
            leave();
            return;
        }
        Store.Session session = store.session(now);
        if (!session.active || session.adult || session.expired) {
            leave();
            return;
        }
        board = HomeBoard.create(this);
        board.tickClock(false);
        board.setGreeting(session.name);
        board.setStatus(Gate.watchLabel(session.free, session.left, true));
        if (!Devices.guardOn(this)) board.warn("守衛還沒打開，其他程式還關不住。");
        board.pill("訊號源", this::inputs);
        board.pill("切換帳號", () -> {
            store.end("");
            leave();
        });
        board.pill("更多", this::more);
        board.setTiles(Devices.shown(this), new ArrayList<>());
        board.bind(false);
        setContentView(board.root);
        WeatherBoard.attach(this, board);
        tick = () -> {
            if (isFinishing()) return;
            long at = System.currentTimeMillis();
            if (store.lockIfClosed(at)) {
                leave();
                return;
            }
            Store.Session current = store.session(at);
            if (!current.active || current.adult || current.expired) {
                leave();
                return;
            }
            board.setStatus(Gate.watchLabel(current.free, current.left, true));
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

    private void more() {
        List<HomeBoard.Choice> rows = new ArrayList<>();
        Store.Session session = store.session(System.currentTimeMillis());
        rows.add(new HomeBoard.Choice("修改密碼", false, () -> startActivity(new Intent(this, PinActivity.class)
                .putExtra("id", session.id).putExtra("change", true))));
        rows.add(new HomeBoard.Choice("加時間", false, () -> startActivity(new Intent(this, PinActivity.class)
                .putExtra("id", "adult").putExtra("grant", true))));
        rows.add(new HomeBoard.Choice("換成家長", false, () -> startActivity(new Intent(this, PinActivity.class)
                .putExtra("id", "adult").putExtra("takeover", true))));
        rows.add(new HomeBoard.Choice("結束收看", false, () -> {
            store.end("已結束收看");
            leave();
        }));
        board.showPanel(board.greeting.getText().toString(), rows);
    }

    private void leave() {
        startActivity(new Intent(this, PickerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
