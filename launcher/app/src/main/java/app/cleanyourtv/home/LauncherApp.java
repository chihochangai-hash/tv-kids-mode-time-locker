package app.cleanyourtv.home;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;

/** Counts real resume and pause only. Redrawing a page must not look like a second page. */
public class LauncherApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                Pages.resumed(activity);
                Front.home(activity.getPackageName(), SystemClock.uptimeMillis());
            }

            @Override
            public void onActivityPaused(Activity activity) {
                Pages.paused(activity);
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
