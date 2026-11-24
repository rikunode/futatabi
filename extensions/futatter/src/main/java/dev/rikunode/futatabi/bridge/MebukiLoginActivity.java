package dev.rikunode.futatabi.bridge;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/**
 * Owner-operated Mebuki login WebView. No real login is executed automatically.
 * Credentials are copied only after an explicit session-check action, and never logged.
 * Cookie attributes/expiry are not exposed by CookieManager.getCookie().
 */
public final class MebukiLoginActivity extends Activity {
    private static final String SITE = "https://mebuki.moe";
    private static final String PREF = "futatabi_extension_private";
    private WebView browser;
    private TextView status;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setTitle("めぶきログイン");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE);
        status = new TextView(this);
        status.setText("本人操作でログイン後、セッション確認を押してください。Cookie属性と期限は未確定です。");
        status.setPadding(20, 16, 20, 16);
        layout.addView(status);

        Button check = new Button(this);
        check.setText("ログイン済みセッションを確認して端末内へ保存");
        check.setOnClickListener(view -> verifyAndStore());
        layout.addView(check);

        Button erase = new Button(this);
        erase.setText("保存したセッション情報だけを削除");
        erase.setOnClickListener(view -> {
            getSharedPreferences(PREF, MODE_PRIVATE).edit().remove("mebuki_cookie_header").commit();
            status.setText("アプリ専用の保存情報を消去しました。WebView内のサイトCookieは残ります。");
        });
        layout.addView(erase);

        EditText slug = new EditText(this);
        slug.setSingleLine(true);
        slug.setHint("スレURLの /t/ 以下のID（任意）");
        layout.addView(slug);
        Button inspect = new Button(this);
        inspect.setText("保存済み認証で読取契約を確認（値は記録しない）");
        inspect.setOnClickListener(view -> inspectContract(slug.getText().toString().trim()));
        layout.addView(inspect);

        browser = new WebView(this);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true); // The official Better Auth interface requires JS.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                String scheme = target.getScheme();
                if ("https".equals(scheme)) return false; // OAuth providers have their own HTTPS domains.
                if ("http".equals(scheme) && "127.0.0.1".equals(target.getHost())) return false;
                return true; // No file:, javascript:, data:, or external app scheme navigation.
            }
        });
        layout.addView(browser, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(layout);
        browser.loadUrl(SITE);
    }

    private void verifyAndStore() {
        String current = browser.getUrl();
        Uri uri = current == null ? null : Uri.parse(current);
        if (uri == null || !"https".equals(uri.getScheme()) || !"mebuki.moe".equals(uri.getHost())) {
            status.setText("めぶき公式サイトに戻ってから確認してください。");
            return;
        }
        String cookies = CookieManager.getInstance().getCookie(SITE);
        if (!MebukiSession.safeCookie(cookies)) {
            status.setText("有効なCookieがありません。本人ログインが必要です。");
            return;
        }
        status.setText("セッションを検証中…");
        new Thread(() -> {
            MebukiSession.State state = MebukiSession.verify(cookies);
            boolean saved = false;
            if (state == MebukiSession.State.AUTHENTICATED) {
                CookieManager.getInstance().flush();
                saved = getSharedPreferences(PREF, MODE_PRIVATE).edit()
                        .putString(MebukiSession.KEY, cookies).commit();
            }
            final boolean persisted = saved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                status.setText(persisted ? "認証済みセッションを端末内に保存しました。" :
                        "保存未完了：" + state.name());
            });
        }, "futatabi-mebuki-session-check").start();
    }

    private void inspectContract(String slug) {
        if (!slug.isEmpty() && !slug.matches("[A-Za-z0-9_-]{8,24}")) {
            status.setText("スレIDの形式が不正です。");
            return;
        }
        final String cookie = MebukiSession.saved(this);
        final org.json.JSONArray names = MebukiSession.cookieNames(CookieManager.getInstance().getCookie(SITE));
        status.setText("読取契約を確認中…");
        new Thread(() -> {
            String result;
            try {
                if (MebukiSession.verify(cookie) != MebukiSession.State.AUTHENTICATED) {
                    JSONObject meta = new JSONObject();
                    meta.put("firstPartyCookieNames", names);
                    meta.put("session", "UNVERIFIED_OR_NOT_SAVED");
                    result = meta.toString(2);
                } else {
                    JSONObject meta = new JSONObject();
                    meta.put("firstPartyCookieNames", names);
                    meta.put("session", MebukiSession.inspectRead("/api/auth/get-session", cookie));
                    meta.put("catalog", MebukiSession.inspectRead("/api/catalog", cookie));
                    if (!slug.isEmpty()) meta.put("messages", MebukiSession.inspectRead("/api/thread/" + slug + "/messages", cookie));
                    result = meta.toString(2);
                }
            } catch (Exception ex) { result = "契約取得失敗：" + ex.getClass().getSimpleName(); }
            final String report = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                status.setText("読取契約確認終了。");
                new android.app.AlertDialog.Builder(this).setTitle("値を含まない契約情報")
                        .setMessage(report).setPositiveButton("OK", null).show();
            });
        }, "futatabi-mebuki-contract-inspect").start();
    }

    @Override protected void onDestroy() {
        if (browser != null) {
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
