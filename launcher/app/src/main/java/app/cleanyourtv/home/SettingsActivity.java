package app.cleanyourtv.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Parent controls. The television itself stays on the adult home. */
public class SettingsActivity extends TvActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private TextView status;
    private TextView trust;
    private Runnable tick;

    @Override
    public void onBackPressed() {
        Store.Session session = Store.get(this).session(System.currentTimeMillis());
        if (session.active && session.adult) {
            startActivity(new Intent(this, AdultActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        show();
    }

    private void show() {
        handler.removeCallbacksAndMessages(null);
        store = Store.get(this);
        Store.Session session = store.session(System.currentTimeMillis());
        if (!session.active || !session.adult) {
            finish();
            return;
        }
        store.ensureCloud();
        LinearLayout page = Ui.page(this);
        page.addView(Ui.title(this, "家長設定"));
        page.addView(Ui.note(this, "兒童平日 8:00–21:00、週六日 8:00–22:00 才能登入。過點直接鎖住，剩下的時間留到下次。"));

        addRow(page, "修改家長密碼", () -> startActivity(new Intent(this, PinActivity.class)
                .putExtra("id", "adult").putExtra("change", true)));

        TextView add = Ui.row(this, "新增小朋友");
        add.setOnClickListener(v -> startActivity(new Intent(this, AddKidActivity.class)));
        page.addView(add);

        for (Store.Person person : store.people()) {
            if (person.adult) continue;
            long bank = store.bankToday(person.id, System.currentTimeMillis());
            String left = store.freeToday(person.id, System.currentTimeMillis())
                    ? "，今天不限"
                    : (bank > 0
                    ? "，還剩 " + Gate.clock((int) ((bank + 999) / 1000))
                    : (store.spent(person.id) ? "，今天用完" : ""));
            page.addView(Ui.section(this, person.name + "，下次 " + person.minutes + " 分鐘" + left));
            addRow(page, "修改密碼", () -> startActivity(new Intent(this, PinActivity.class)
                    .putExtra("id", person.id).putExtra("change", true).putExtra("reset", true)));
            addRow(page, "下次 30 分鐘", () -> {
                store.setMinutes(person.id, 30);
                show();
            });
            addRow(page, "下次 60 分鐘", () -> {
                store.setMinutes(person.id, 60);
                show();
            });
            addRow(page, "刪除 " + person.name, () -> new AlertDialog.Builder(this)
                    .setTitle("刪除 " + person.name)
                    .setMessage("這個帳號和密碼會一起刪掉。")
                    .setPositiveButton("刪除", (d, w) -> {
                        store.remove(person.id);
                        show();
                    })
                    .setNegativeButton("取消", null)
                    .show());
        }

        Store.Person last = store.lastKid();
        if (last != null) {
            addRow(page, "再給 " + last.name + " 時間", () -> startActivity(new Intent(this, TimeActivity.class)));
        }

        page.addView(Ui.section(this, "手機加時"));
        TextView code = new TextView(this);
        code.setText(store.pairCode());
        code.setTextSize(42);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextColor(0xFFFFFFFF);
        page.addView(code);
        page.addView(Ui.note(this, "先在程式裡填上你自己的雲端位址。手機打開那個網頁，輸入主機 PIN，再輸入這個配對碼。"));
        status = Ui.note(this, CloudLoop.text);
        page.addView(status);
        trust = Ui.row(this, "信任憑證");
        trust.setOnClickListener(v -> {
            if (CloudLoop.seenFp != null && !CloudLoop.seenFp.isEmpty()) store.setFingerprint(CloudLoop.seenFp);
            refreshCloud();
        });
        page.addView(trust);
        TextView cloudToggle = Ui.row(this, store.cloudOn() ? "關閉雲端" : "開啟雲端");
        cloudToggle.setOnClickListener(v -> {
            store.setCloudOn(!store.cloudOn());
            if (!store.cloudOn()) CloudLoop.text = "雲端遙控已關閉";
            show();
        });
        page.addView(cloudToggle);
        addRow(page, "開啟守衛", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addRow(page, "允許倒數浮在畫面上", () -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        addRow(page, "電視設定", () -> Devices.openSettings(this));
        addRow(page, "切換帳號", () -> {
            store.end("");
            startActivity(new Intent(this, PickerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        setContentView(Ui.screen(this, Ui.scroll(this, page)));
        refreshCloud();
        tick = () -> {
            if (!isFinishing()) {
                refreshCloud();
                handler.postDelayed(tick, 1000);
            }
        };
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(tick, 1000);
    }

    private void addRow(LinearLayout page, String label, Runnable action) {
        TextView row = Ui.row(this, label);
        row.setOnClickListener(v -> action.run());
        page.addView(row);
    }

    private void refreshCloud() {
        if (status != null) status.setText(CloudLoop.text == null || CloudLoop.text.isEmpty() ? "正在連上雲端…" : CloudLoop.text);
        String seen = CloudLoop.seenFp == null ? "" : CloudLoop.seenFp;
        String stored = store.fingerprint();
        boolean differ = !seen.isEmpty() && !seen.equalsIgnoreCase(stored);
        if (trust != null) {
            trust.setVisibility(differ ? android.view.View.VISIBLE : android.view.View.GONE);
            trust.setText("信任憑證 " + Gate.grouped(seen));
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
