package app.tvkids.timelocker;

import android.content.Intent;

public class HomeActivity extends TvActivity {
    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        route();
    }

    private void route() {
        Store store = Store.get(this);
        if (!store.hasAdultPin()) {
            openPin("adult", true, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (store.lockIfClosed(now)) {
            open(PickerActivity.class);
            return;
        }
        Store.Session session = store.session(now);
        if (session.active && !session.expired && session.adult) open(AdultActivity.class);
        else if (session.active && !session.expired) open(KidsActivity.class);
        else open(PickerActivity.class);
    }

    private void open(Class<?> activity) {
        Intent intent = new Intent(this, activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void openPin(String id, boolean create, boolean takeover) {
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("id", id);
        intent.putExtra("create", create);
        intent.putExtra("takeover", takeover);
        startActivity(intent);
    }
}
