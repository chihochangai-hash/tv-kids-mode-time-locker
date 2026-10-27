package app.tvkids.timelocker;

import android.widget.Button;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class KidAppsActivity extends TvActivity {
    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        String id = getIntent().getStringExtra("id");
        Store store = Store.get(this);
        Store.Person person = id == null ? null : store.person(id);
        if (person == null || person.adult) {
            finish();
            return;
        }
        List<String> selected = new ArrayList<>(store.apps(id));
        LinearLayout column = Ui.column(this);
        column.addView(Ui.title(this, person.name + " 的 App"));
        column.addView(Ui.note(this, "TizenTube 和 SmartTube 固定在兒童畫面。官方 YouTube 不能勾。"));
        for (Devices.Item app : Devices.list(this)) {
            Button button = Ui.button(this, label(selected, app));
            Ui.stack(button, Ui.dp(this, 8));
            button.setOnClickListener(v -> {
                if (selected.contains(app.pkg)) selected.remove(app.pkg);
                else selected.add(app.pkg);
                store.setApps(id, selected);
                button.setText(label(selected, app));
            });
            column.addView(button);
        }
        setContentView(Ui.scroll(this, column));
    }

    private static String label(List<String> selected, Devices.Item app) {
        return (selected.contains(app.pkg) ? "✓ " : "") + app.label;
    }
}
