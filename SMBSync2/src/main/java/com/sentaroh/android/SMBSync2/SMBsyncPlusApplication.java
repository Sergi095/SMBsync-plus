package com.sentaroh.android.SMBSync2;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

/** Tracks whether the entire app, rather than one screen, left the foreground. */
public final class SMBsyncPlusApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private static SMBsyncPlusApplication instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int startedActivities;
    private long backgroundEpoch;
    private Runnable pendingBackground;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(this);
    }

    static long backgroundEpoch() {
        return instance == null ? 0 : instance.backgroundEpoch;
    }

    @Override public void onActivityStarted(Activity activity) {
        startedActivities++;
        if (pendingBackground != null) {
            handler.removeCallbacks(pendingBackground);
            pendingBackground = null;
        }
        GlobalParameters gp = GlobalWorkArea.getGlobalParameters(activity);
        boolean protectedApp = !gp.settingSecurityApplicationPasswordHashValue.isEmpty()
                && gp.settingSecurityApplicationPasswordUseAppStartup;
        if (!protectedApp) return;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (!gp.appPasswordAuthValidated
                && !(activity instanceof ActivityMain)
                && !(activity instanceof FileExplorerActivity)
                && !(activity instanceof SmbConnectionsActivity)
                && !(activity instanceof ActivityPasswordSettings)) {
            activity.getWindow().getDecorView().setVisibility(View.INVISIBLE);
            Intent lock = new Intent(activity, ActivityMain.class);
            lock.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(lock);
        }
    }

    @Override public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && !activity.isChangingConfigurations()) {
            pendingBackground = () -> {
                pendingBackground = null;
                if (startedActivities == 0) {
                    backgroundEpoch++;
                    GlobalWorkArea.getGlobalParameters(this).appPasswordAuthValidated = false;
                }
            };
            handler.postDelayed(pendingBackground, 250);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
