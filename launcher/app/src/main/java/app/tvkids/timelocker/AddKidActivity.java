package app.tvkids.timelocker;

import android.graphics.Color;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AddKidActivity extends TvActivity {
    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        LinearLayout column = Ui.column(this);
        column.addView(Ui.title(this, "新增小朋友"));
        TextView message = Ui.note(this, "1 到 16 個字，不能跟現有的名字一樣。");
        column.addView(message);
        EditText name = new EditText(this);
        name.setHint("名字");
        name.setTextSize(24);
        name.setTextColor(Color.WHITE);
        name.setHintTextColor(0xFF94A3B8);
        name.setSingleLine(true);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Ui.stack(name, Ui.dp(this, 12));
        column.addView(name);
        Button save = Ui.button(this, "儲存");
        Ui.stack(save, Ui.dp(this, 12));
        Runnable submit = () -> {
            if (Store.get(this).addKid(name.getText().toString())) finish();
            else message.setText("名字請用 1 到 16 個字，而且不能跟現有的一樣。");
        };
        save.setOnClickListener(v -> submit.run());
        name.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });
        column.addView(save);
        setContentView(Ui.scroll(this, column));
        name.requestFocus();
    }
}
