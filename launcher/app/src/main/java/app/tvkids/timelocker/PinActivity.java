package app.tvkids.timelocker;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Google TV pad: up and down change the row, left center right type it, down on the last row types 0. */
public class PinActivity extends TvActivity {
    private String id = "";
    private boolean create;
    private boolean change;
    private boolean reset;
    private boolean checkingOld;
    private boolean takeover;
    private boolean topup;
    private boolean topupFree;
    private String topupKid = "";
    private int topupMinutes;
    private String first = "";
    private boolean confirming;
    private final StringBuilder entry = new StringBuilder();
    private TextView heading;
    private TextView message;
    private TextView upMark;
    private TextView leftDigit;
    private TextView centerDigit;
    private TextView rightDigit;
    private TextView downMark;
    private final TextView[] legend = new TextView[4];
    private final TextView[] slots = new TextView[6];
    private Store store;
    private Store.Person person;
    private int group;
    private boolean longSent;
    private long lastType;

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        id = getIntent().getStringExtra("id");
        if (id == null) id = "";
        create = getIntent().getBooleanExtra("create", false);
        change = getIntent().getBooleanExtra("change", false);
        reset = getIntent().getBooleanExtra("reset", false);
        takeover = getIntent().getBooleanExtra("takeover", false);
        topup = getIntent().getBooleanExtra("topup", false);
        topupFree = getIntent().getBooleanExtra("free", false);
        topupKid = getIntent().getStringExtra("kid");
        if (topupKid == null) topupKid = "";
        topupMinutes = getIntent().getIntExtra("minutes", 0);
        store = Store.get(this);
        person = store.person(id);
        if (person == null) {
            finish();
            return;
        }
        checkingOld = change && !reset && person.hasPin;
        confirming = false;
        first = "";
        entry.setLength(0);
        group = 0;
        longSent = false;
        build();
        showLock();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_ESCAPE) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) onBackPressed();
            return true;
        }
        boolean center = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        if (center) return onCenter(event);
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) {
            if (isPad(key)) return true;
            return super.dispatchKeyEvent(event);
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (group > 0) group--;
            paint();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (PinPad.downTypesZero(group)) type('0');
            else group++;
            paint();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            type(PinPad.digit(group, PinPad.LEFT));
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            type(PinPad.digit(group, PinPad.RIGHT));
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean onCenter(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                if (!longSent && entry.length() >= 4) {
                    longSent = true;
                    submit();
                }
                return true;
            }
            longSent = false;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !longSent) {
            type(PinPad.digit(group, PinPad.CENTER));
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (entry.length() > 0) {
            entry.deleteCharAt(entry.length() - 1);
            paint();
            return;
        }
        if (create && "adult".equals(id) && !change) return;
        finish();
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 48);
        root.setPadding(pad, Ui.dp(this, 36), pad, Ui.dp(this, 36));
        root.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heading = Ui.title(this, title());
        heading.setTextSize(40);
        left.addView(heading);
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.HORIZONTAL);
        who.setGravity(Gravity.CENTER_VERTICAL);
        who.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 12));
        TextView avatar = new TextView(this);
        String letter = new String(Character.toChars(person.name.codePointAt(0)));
        avatar.setText(letter);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(22);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        int size = Ui.dp(this, 48);
        GradientDrawable face = new GradientDrawable();
        face.setShape(GradientDrawable.OVAL);
        face.setColor(Ui.profileColor(person.id));
        avatar.setBackground(face);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        who.addView(avatar);
        TextView name = new TextView(this);
        name.setText(person.name);
        name.setTextColor(Color.WHITE);
        name.setTextSize(20);
        name.setPadding(Ui.dp(this, 14), 0, 0, 0);
        who.addView(name);
        left.addView(who);
        String help = "上下換組。左、中、右輸入這一組的數字。最下面再按下一格是 0。畫面上只會出現圓點。滿 4 位後長按中間鍵送出，返回鍵刪除。";
        if (topup) {
            Store.Person kid = store.person(topupKid);
            String childName = kid == null ? "這個帳號" : kid.name;
            String amount = topupFree
                    ? "今天不限時。用量不限，但仍要在" + Gate.kidWindowNow(System.currentTimeMillis()) + "。只有今天，過了午夜恢復原來的時間"
                    : Gate.pickerAddLabel(topupMinutes);
            if (amount.isEmpty()) amount = "時間";
            help = "要給 " + childName + " " + amount + "。密碼正確才會加上，不會直接開看。小朋友還是要用自己的密碼登入。" + help;
        }
        message = Ui.note(this, help);
        left.addView(message);
        root.addView(left);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = new TextView(this);
        label.setText("輸入密碼");
        label.setTextSize(12);
        label.setLetterSpacing(0.18f);
        label.setTextColor(0xFFD5DEE6);
        label.setPadding(0, 0, 0, Ui.dp(this, 10));
        right.addView(label);
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < slots.length; i++) {
            TextView slot = new TextView(this);
            slot.setGravity(Gravity.CENTER);
            slot.setTextSize(18);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28));
            lp.leftMargin = Ui.dp(this, 6);
            lp.rightMargin = Ui.dp(this, 6);
            slot.setLayoutParams(lp);
            slots[i] = slot;
            dots.addView(slot);
        }
        right.addView(dots);

        LinearLayout padRow = new LinearLayout(this);
        padRow.setOrientation(LinearLayout.HORIZONTAL);
        padRow.setGravity(Gravity.CENTER);
        padRow.setPadding(0, Ui.dp(this, 18), 0, 0);
        LinearLayout circle = new LinearLayout(this);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER);
        int diameter = Ui.dp(this, 280);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFFF4F1EA);
        circle.setBackground(disc);
        LinearLayout.LayoutParams discLp = new LinearLayout.LayoutParams(diameter, diameter);
        circle.setLayoutParams(discLp);
        upMark = mark("▲", 0xFF8C877C);
        circle.addView(upMark);
        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.HORIZONTAL);
        numbers.setGravity(Gravity.CENTER);
        leftDigit = digitView();
        centerDigit = digitView();
        rightDigit = digitView();
        numbers.addView(leftDigit);
        numbers.addView(centerDigit);
        numbers.addView(rightDigit);
        circle.addView(numbers);
        downMark = mark("▼", 0xFF1C1C1E);
        circle.addView(downMark);
        padRow.addView(circle);

        LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        stack.setPadding(Ui.dp(this, 22), 0, 0, 0);
        stack.setGravity(Gravity.CENTER_VERTICAL);
        String[] labels = {"1   2   3", "4   5   6", "7   8   9", "0"};
        for (int i = 0; i < labels.length; i++) {
            TextView row = new TextView(this);
            row.setText(labels[i]);
            row.setTextSize(16);
            row.setTextColor(0xFFE8EEF2);
            row.setGravity(Gravity.CENTER);
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            legend[i] = row;
            stack.addView(row);
        }
        padRow.addView(stack);
        right.addView(padRow);
        root.addView(right);
        setContentView(Ui.screen(this, root));
        root.setFocusable(true);
        root.requestFocus();
        paint();
    }

    private TextView digitView() {
        TextView view = new TextView(this);
        view.setTextSize(36);
        view.setTextColor(0xFF1C1C1E);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        int w = Ui.dp(this, 64);
        view.setLayoutParams(new LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private TextView mark(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(18);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        return view;
    }

    private void type(char digit) {
        long now = SystemClock.uptimeMillis();
        if (now - lastType < 90) return;
        lastType = now;
        if (digit == 0 || entry.length() >= 6) return;
        entry.append(digit);
        paint();
        if (entry.length() == 6) submit();
    }

    private void paint() {
        if (heading != null) heading.setText(title());
        char[] row = PinPad.ROWS[group];
        leftDigit.setText(String.valueOf(row[0]));
        centerDigit.setText(String.valueOf(row[1]));
        rightDigit.setText(String.valueOf(row[2]));
        upMark.setAlpha(group == 0 ? 0.35f : 1f);
        if (PinPad.downTypesZero(group)) {
            downMark.setText("0");
            downMark.setTextSize(36);
            downMark.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            downMark.setText("▼");
            downMark.setTextSize(18);
            downMark.setTypeface(Typeface.DEFAULT);
        }
        for (int i = 0; i < legend.length; i++) {
            boolean on = i == group || (group == 2 && i == 3);
            GradientDrawable plate = new GradientDrawable();
            plate.setCornerRadius(Ui.dp(this, 16));
            plate.setColor(on ? 0x22FFFFFF : Color.TRANSPARENT);
            plate.setStroke(Ui.dp(this, on ? 2 : 0), on ? 0xFFE8EEF2 : Color.TRANSPARENT);
            legend[i].setBackground(plate);
        }
        for (int i = 0; i < slots.length; i++) {
            boolean filled = i < entry.length();
            slots[i].setText(filled ? "●" : "—");
            slots[i].setTextColor(filled ? Color.WHITE : 0x66FFFFFF);
        }
    }

    private String title() {
        if (topup) return "輸入家長密碼";
        if (confirming) return "再輸入一次";
        if (change && checkingOld) return "輸入目前的密碼";
        if (change) return "設定新密碼";
        if (create && person.adult) return "設定家長密碼";
        if (create) return "為 " + person.name + " 設定密碼";
        return "輸入 " + person.name + " 的密碼";
    }

    private void submit() {
        int wait = store.lockLeft(id, System.currentTimeMillis());
        if (wait > 0) {
            message.setText("請等 " + wait + " 秒");
            return;
        }
        if (!PinBook.digits(entry.toString())) {
            message.setText("請輸入 4 到 6 位。滿 4 位後長按中間鍵送出。");
            return;
        }
        if (change && checkingOld) {
            String verdict = store.verify(id, entry.toString(), System.currentTimeMillis());
            entry.setLength(0);
            group = 0;
            if ("locked".equals(verdict)) {
                showLock();
                paint();
                return;
            }
            if (!"ok".equals(verdict)) {
                message.setText("密碼不對");
                paint();
                return;
            }
            checkingOld = false;
            message.setText("請設定新的 4 到 6 位密碼，接著再輸入一次。");
            paint();
            return;
        }
        if (create || change) {
            if (!confirming) {
                first = entry.toString();
                entry.setLength(0);
                confirming = true;
                message.setText("再輸入一次。仍然只會看到圓點。");
                group = 0;
                paint();
                return;
            }
            if (!first.equals(entry.toString())) {
                first = "";
                entry.setLength(0);
                confirming = false;
                message.setText("兩次不一樣，請重設");
                group = 0;
                paint();
                return;
            }
            if (!store.setPin(id, first)) {
                message.setText("請輸入 4 到 6 位");
                return;
            }
            if (change) {
                go(reset || person.adult ? SettingsActivity.class : KidsActivity.class);
                return;
            }
            long now = System.currentTimeMillis();
            if (person.adult) go(PickerActivity.class);
            else if (!canWatch(now)) go(PickerActivity.class);
            else if (!store.begin(id, now, true)) usedUp();
            else go(KidsActivity.class);
            return;
        }
        String verdict = store.verify(id, entry.toString(), System.currentTimeMillis());
        if ("locked".equals(verdict)) {
            showLock();
            entry.setLength(0);
            paint();
            return;
        }
        if (!"ok".equals(verdict)) {
            message.setText("密碼不對");
            entry.setLength(0);
            group = 0;
            paint();
            return;
        }
        long now = System.currentTimeMillis();
        if (topup) {
            boolean added = topupFree ? store.grantToday(topupKid, now) : store.topUp(topupKid, topupMinutes, now);
            if (added) go(PickerActivity.class);
            else {
                message.setText("這段時間沒有加上");
                entry.setLength(0);
                group = 0;
                paint();
            }
            return;
        }
        if (person.adult && getIntent().getBooleanExtra("grant", false)) {
            startActivity(new Intent(this, TimeActivity.class));
            finish();
            return;
        }
        if (takeover || person.adult) {
            if (takeover) store.end("");
            store.begin("adult", now, false);
            go(getIntent().getBooleanExtra("manage", false) ? SettingsActivity.class : AdultActivity.class);
        } else if (!canWatch(now)) {
            message.setText("現在不能看。" + Gate.kidWindowNow(now) + "。剩下的時間會留著。");
        } else if (!store.begin(id, now, true)) {
            usedUp();
        } else {
            go(KidsActivity.class);
        }
    }

    private boolean canWatch(long now) {
        return Gate.kidAllowedNow(now);
    }

    private void usedUp() {
        if (message != null) message.setText("時間已用完。請家長加時間後再登入。");
        entry.setLength(0);
        group = 0;
        paint();
    }

    private void showLock() {
        int wait = store.lockLeft(id, System.currentTimeMillis());
        if (wait > 0 && message != null) message.setText("請等 " + wait + " 秒");
    }

    private void go(Class<?> activity) {
        Intent intent = new Intent(this, activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private static boolean isPad(int key) {
        return key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
    }
}
