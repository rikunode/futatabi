package dev.rikunode.futatabi.bridge;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import android.widget.Button;
import android.view.MotionEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import org.json.JSONObject;

/** Isolated test APK only. Never use with real account data or on owner device. */
public final class AimogeLoginWebViewTest extends Instrumentation {
    private static final String STATE = "S".repeat(43);
    private static final String CODE = "C".repeat(43);
    private static final String VERIFIER = "V".repeat(43);
    private static final String TOKEN = "synthetic-fixture-only";
    private static volatile int exchanges;
    private static volatile boolean failDns = true;
    private static final String CALLBACK = "http://127.0.0.1:18765/ext/login/aimoge/callback";
    private static void check(boolean ok, String stage) {
        if (!ok) throw new AssertionError(stage);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static final class Mock extends HttpURLConnection {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        Mock(URL u) { super(u); }
        public void connect() {}
        public void disconnect() {}
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() throws IOException {
            check(url.getHost().equals("api.nijiurachan.net") && url.getPath().equals("/app-credentials"), "unexpected destination");
            exchanges++;
            if (failDns) throw new UnknownHostException("fixture");
            return data;
        }
        public int getResponseCode() { return 201; }
        public InputStream getInputStream() throws IOException {
            try {
                JSONObject req = new JSONObject(data.toString("UTF-8"));
                check(req.getString("code").equals(CODE) && req.getString("codeVerifier").equals(VERIFIER)
                        && req.getString("redirectUri").equals(CALLBACK), "PKCE payload");
                JSONObject out = new JSONObject();
                out.put("credential", TOKEN);
                out.put("expiresAt", java.time.Instant.now().plusSeconds(600).toString());
                return new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { throw new IOException(e); }
        }
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(object);
    }
    private void waitFor(java.util.function.BooleanSupplier predicate, String stage) throws Exception {
        long until = SystemClock.elapsedRealtime() + 10000;
        while (!predicate.getAsBoolean() && SystemClock.elapsedRealtime() < until) Thread.sleep(100);
        check(predicate.getAsBoolean(), stage);
    }
    @Override public void onStart() {
        Bundle out = new Bundle();
        try {
            Context ctx = getTargetContext();
            SharedPreferences prefs = ctx.getSharedPreferences("futatabi_extension_private", Context.MODE_PRIVATE);
            check(prefs.getString("credential", "").isEmpty(), "not a fresh test profile");
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
                protected URLConnection openConnection(URL u) { return new Mock(u); }
            } : null);
            check(prefs.edit().putString("pkce_state", STATE).putString("pkce_verifier", VERIFIER)
                    .putLong("pkce_started", System.currentTimeMillis()).commit(), "fixture state");
            Intent i = new Intent(ctx, AimogeLoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(AimogeLoginActivity.AUTH_URL, "https://nijiurachan.net/b/aimg/app-auth?requestId=fixture");
            Activity activity = startActivitySync(i);
            WebView browser = (WebView) field(activity, "browser");
            Button retry = (Button) field(activity, "retry");
            check(browser != null, "webview");
            // Synthetic document only, not injected into the real official page.
            runOnMainSync(() -> {
                browser.stopLoading();
                browser.getSettings().setBlockNetworkLoads(true);
                browser.loadDataWithBaseURL("https://nijiurachan.net/",
                    "<html><body style='margin:0'><a style='display:block;width:100%;height:200px' href='"
                    + CALLBACK + "?state=" + STATE + "&amp;code=" + CODE + "'>Fixture callback</a></body></html>",
                    "text/html", "UTF-8", null);
            });
            Thread.sleep(1500);
            int[] position = new int[2];
            runOnMainSync(() -> browser.getLocationOnScreen(position));
            long t = SystemClock.uptimeMillis();
            sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,position[0]+35,position[1]+35,0));
            sendPointerSync(MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,position[0]+35,position[1]+35,0));
            waitFor(() -> exchanges == 1, "actual callback intercepted");
            waitFor(() -> { final boolean[] yes={false}; runOnMainSync(() -> yes[0]=retry.isEnabled()); return yes[0]; }, "manual retry enabled");
            check(prefs.contains("pkce_verifier") && prefs.getString("credential","").isEmpty(), "DNS retains PKCE without credential");
            failDns = false;
            runOnMainSync(() -> retry.performClick());
            waitFor(() -> TOKEN.equals(prefs.getString("credential","")), "credential stored");
            waitFor(activity::isFinishing, "activity returned");
            check(exchanges == 2 && !prefs.contains("pkce_state"), "exactly one retry and state consumed");
            out.putString("stream", "PASS: real WebView synthetic link -> native callback -> DNS failure -> manual retry -> private save -> Activity finish; no real authentication.\n");
            finish(Activity.RESULT_OK, out);
        } catch (Throwable e) {
            out.putString("stream", "FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n");
            finish(Activity.RESULT_CANCELED, out);
        }
    }
}
