package dev.rikunode.futatabi.bridge;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native notification of the stock selected-thread preference, not an HTTP trigger. */
public final class MebukiAutoReader implements Application.ActivityLifecycleCallbacks {
    private static final String MAIN = "com.Nanasaki.ftutter.MainActivity";
    private static final Pattern THREAD = Pattern.compile(
            "http://127[.]0[.]0[.]1:18765/mebuki/res/([0-9]{13})[.]htm");
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static volatile WeakReference<Activity> foreground = new WeakReference<>(null);
    private static boolean installed, launching;

    static void install(Application app) {
        if (!installed) {
            installed = true;
            app.registerActivityLifecycleCallbacks(new MebukiAutoReader());
        }
    }

    static String target(String key, String value, Boolean committed) {
        if (!Boolean.TRUE.equals(committed) || !"flutter.opening".equals(key) || value == null)
            return null;
        Matcher m = THREAD.matcher(value);
        return m.matches() ? m.group(1) : null;
    }

    // Called after the original Pigeon setString commit, with its unmodified result.
    public static void onSelectionSaved(String key, String value, Boolean committed) {
        String id = target(key,value,committed);
        if (id == null) return;
        Activity origin = foreground.get();
        if (origin == null) return;
        UI.post(() -> {
            if (foreground.get() != origin || launching || origin.isFinishing()
                    || origin.isDestroyed() || !origin.hasWindowFocus()) return;
            try {
                if (!value.equals(origin.getSharedPreferences("FlutterSharedPreferences",0)
                        .getString("flutter.opening",""))) return;
                if (!MebukiSession.safeCookie(MebukiSession.saved(origin))
                        || new MebukiAdapter(origin).slugForThreadId(id) == null) return;
                launching = true;
                origin.startActivity(new Intent(origin,MebukiReaderActivity.class)
                        .putExtra(MebukiReaderActivity.ID,id));
            } catch (RuntimeException ex) {
                launching = false;
                // The stock notice and its manual link remain available.
            }
        });
    }

    @Override public void onActivityResumed(Activity a) {
        launching = false;
        foreground = new WeakReference<>(MAIN.equals(a.getClass().getName()) ? a : null);
    }
    @Override public void onActivityPaused(Activity a) {
        if (foreground.get() == a) foreground = new WeakReference<>(null);
    }
    @Override public void onActivityCreated(Activity a, Bundle b) {}
    @Override public void onActivityStarted(Activity a) {}
    @Override public void onActivityStopped(Activity a) {}
    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    @Override public void onActivityDestroyed(Activity a) {
        if (foreground.get() == a) foreground = new WeakReference<>(null);
    }
}
