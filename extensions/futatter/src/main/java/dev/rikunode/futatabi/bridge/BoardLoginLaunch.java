package dev.rikunode.futatabi.bridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reserved internal signal from the patched catalog click, not an external URL. */
public final class BoardLoginLaunch {
    private BoardLoginLaunch() {}
    private static final String SIGNAL = "assets/images/ad.svg";
    private static final Pattern REPLY = Pattern.compile(
            "assets/images/ad[.]svghttp://127[.]0[.]0[.]1:18765/(aimoge|animanch)/res/([0-9]{13})[.]htm");


    public static Boolean tryLaunch(Activity activity, String url) {
        if (url == null || !url.startsWith(SIGNAL)) return null;
        boolean login = SIGNAL.equals(url);
        Matcher reply = REPLY.matcher(url);
        if (!login && !reply.matches()) return Boolean.FALSE;
        // Consume the reserved signal even when the Activity is no longer eligible.
        // Never launch a browser or a background Activity for this signal.
        if (activity == null || !activity.getClass().getName().equals("com.Nanasaki.ftutter.MainActivity")
                || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()
                || Looper.myLooper() != Looper.getMainLooper()) return Boolean.FALSE;
        try {
            Intent intent;
            if (login) intent = new Intent(activity, FutatabiHomeActivity.class);
            else {
                intent = new Intent(activity, ThreadReplyActivity.class);
                intent.putExtra("board", reply.group(1));
                intent.putExtra("virtualId", reply.group(2));
            }
            activity.startActivity(intent);
            return Boolean.TRUE;
        } catch (RuntimeException ex) {
            Log.w("FutatabiLogin", "Internal launch failed: " + ex.getClass().getSimpleName());
            return Boolean.FALSE;
        }
    }
}
