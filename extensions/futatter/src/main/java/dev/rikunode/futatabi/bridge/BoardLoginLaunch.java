package dev.rikunode.futatabi.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;

/** Reserved internal signal from the patched catalog click, not an external URL. */
public final class BoardLoginLaunch {
    private BoardLoginLaunch() {}

    public static Boolean tryLaunch(Activity activity, String url) {
        if (!"assets/images/ad.svg".equals(url)) return null;
        // Consume the reserved signal even when the Activity is no longer eligible.
        // Never launch a browser or a background Activity for this signal.
        if (activity == null || !activity.getClass().getName().equals("com.Nanasaki.ftutter.MainActivity")
                || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()
                || Looper.myLooper() != Looper.getMainLooper()) return Boolean.FALSE;
        try {
            activity.startActivity(new Intent(activity, FutatabiHomeActivity.class));
            return Boolean.TRUE;
        } catch (RuntimeException ex) {
            Log.w("FutatabiLogin", "Home launch failed: " + ex.getClass().getSimpleName());
            return Boolean.FALSE;
        }
    }
}
