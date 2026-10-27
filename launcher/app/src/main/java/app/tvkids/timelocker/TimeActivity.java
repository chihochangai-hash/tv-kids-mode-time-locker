package app.tvkids.timelocker;

import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Parent stands at the TV and adds minutes with the remote. Same steps the phone may send. */
public class TimeActivity extends TvActivity {
    private Store store;
    private TextView remain;

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        store = Store.get(this);
        long now = System.currentTimeMillis();
        Store.Session session = store.session(now);
        boolean live = session.active && !session.adult && !session.expired;
        Store.Person last = store.lastKid();
        if (!live && last == null) {
            finish();
            return;
        }
        LinearLayout column = Ui.column(this);
        column.addView(Ui.title(this, live ? "加時間" : "再給一輪"));
        remain = Ui.note(this, live ? Gate.timeLabel(session.left, session.paused) : "上一輪已經結束");
        column.addView(remain);
        column.addView(Ui.note(this, "大人密碼已經核對。方向鍵選分鐘，確定就加上。"));
        if (live) {
            add(column, "+1 分鐘", () -> extend("add", 1));
            add(column, "+5 分鐘", () -> extend("add", 5));
            add(column, "+10 分鐘", () -> extend("add", 10));
            add(column, "+30 分鐘", () -> extend("add", 30));
            add(column, "設成 30 分鐘", () -> extend("set", 30));
            add(column, "設成 1 小時", () -> extend("set", 60));
        } else {
            add(column, "再給 " + last.name + " 30 分鐘", () -> fresh(last.id, 30));
            add(column, "再給 " + last.name + " 1 小時", () -> fresh(last.id, 60));
        }
        Button done = Ui.button(this, "完成");
        Ui.stack(done, Ui.dp(this, 18));
        done.setOnClickListener(v -> finish());
        column.addView(done);
        setContentView(Ui.scroll(this, column));
    }

    private void add(LinearLayout column, String label, Runnable action) {
        Button button = Ui.button(this, label);
        Ui.stack(button, Ui.dp(this, 8));
        button.setOnClickListener(v -> action.run());
        column.addView(button);
    }

    private void extend(String op, int minutes) {
        String result = store.grant(op, minutes, System.currentTimeMillis());
        Store.Session session = store.session(System.currentTimeMillis());
        if (!"ok".equals(result) || !session.active || session.expired) {
            remain.setText("這一輪已經結束");
            return;
        }
        remain.setText(Gate.timeLabel(session.left, session.paused));
    }

    private void fresh(String id, int minutes) {
        store.beginMinutes(id, System.currentTimeMillis(), minutes);
        Intent intent = new Intent(this, KidsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
