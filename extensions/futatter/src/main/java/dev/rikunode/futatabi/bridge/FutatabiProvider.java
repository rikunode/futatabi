package dev.rikunode.futatabi.bridge;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs before FlutterActivity. The provider is not exported. Failure must not
 * stop the original app from launching, including when the socket is busy.
 */
public final class FutatabiProvider extends ContentProvider {
    private static final String TAG = "FutatabiBridge";
    private static final String FLUTTER_PREFIX =
            "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!";

    @Override public boolean onCreate() {
        Context context = getContext();
        if (context == null) return true;
        try {
            registerBoards(context);
        } catch (Exception ex) {
            Log.w(TAG, "Board registration skipped: " + ex.getClass().getSimpleName());
        }
        try {
            final Context app = context.getApplicationContext();
            Thread server = new Thread(() -> {
                try { new BridgeServer(app).run(); }
                catch (Exception ex) { Log.w(TAG, "Bridge unavailable: " + ex.getClass().getSimpleName()); }
            }, "futatabi-loopback");
            server.setDaemon(true);
            server.start();
        } catch (Exception ex) {
            Log.w(TAG, "Bridge start skipped: " + ex.getClass().getSimpleName());
        }
        return true;
    }

    private static void registerBoards(Context context) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE);
        String raw = prefs.getString("flutter.itaList", null);
        JSONArray items;
        if (raw == null) {
            // The app creates its default board lazily after terms acceptance.
            // Preserve that stock board even when our provider runs first.
            items = new JSONArray();
            JSONObject defaultBoard = board("ねこ", "https://may.2chan.net/27/");
            defaultBoard.put("favorite", true);
            items.put(defaultBoard.toString());
        } else {
            if (!raw.startsWith(FLUTTER_PREFIX)) return;
            items = new JSONArray(raw.substring(FLUTTER_PREFIX.length()));
        }
        boolean aimoge = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = new JSONObject(items.getString(i));
            String url = item.optString("url");
            if ("http://127.0.0.1:18765/aimoge/".equals(url)) aimoge = true;
        }
        if (aimoge) return;
        // Animanch is intentionally not auto-added until its adapter is verified.
        items.put(board("あいもげ", "http://127.0.0.1:18765/aimoge/").toString());
        // Commit before Flutter reads the preference. No overwrite when invalid.
        prefs.edit().putString("flutter.itaList", FLUTTER_PREFIX + items.toString()).commit();
    }

    private static JSONObject board(String name, String url) throws Exception {
        JSONObject entry = new JSONObject();
        entry.put("name", name);
        entry.put("url", url);
        entry.put("favorite", false);
        entry.put("kakikomiData", JSONObject.NULL);
        entry.put("suretateCount", 0);
        entry.put("sureOpenCount", 0);
        entry.put("resuCount", 0);
        entry.put("soudaneCount", 0);
        return entry;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
