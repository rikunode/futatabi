package dev.rikunode.futatabi.bridge;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays the unmodified official authorization page. Owner approval only. */
public final class AimogeLoginActivity extends Activity {
    static final String AUTH_URL = "authorization_url";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView browser;
    private TextView notice;
    private Button retry;
    private String pendingCallback;
    private boolean busy, completed, pageFailed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Aimoge login");
        String start = getIntent().getStringExtra(AUTH_URL);
        if (AimogeLoginFlow.page(start) != AimogeLoginFlow.Page.OFFICIAL) {
            finish();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        notice = new TextView(this);
        notice.setText("公式認可ページを読み込み中…");
        notice.setPadding(16, 12, 16, 12);
        layout.addView(notice);
        retry = new Button(this);
        retry.setText("再試行");
        retry.setEnabled(true);
        retry.setOnClickListener(v -> {
            if (pendingCallback != null) exchange(pendingCallback);
            else if (!busy && !completed && browser != null) browser.loadUrl(start);
        });
        layout.addView(retry);
        Button close = new Button(this);
        close.setText("ホームへ戻る");
        close.setOnClickListener(v -> { if (!busy) finish(); });
        layout.addView(close);
        setContentView(layout);
        try { browser = new WebView(this); }
        catch (RuntimeException ex) {
            notice.setText("WebViewを起動できません。Android System WebViewを確認してください。");
            retry.setEnabled(false);
            return;
        }
        browser.setBackgroundColor(Color.WHITE);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String target = request.getUrl().toString();
                if (!request.isForMainFrame()) return !"https".equals(request.getUrl().getScheme());
                AimogeLoginFlow.Page page = AimogeLoginFlow.page(target);
                if (page == AimogeLoginFlow.Page.CALLBACK && "GET".equals(request.getMethod())) {
                    if (!busy && !completed && pendingCallback == null) exchange(target);
                    return true;
                }
                if (page == AimogeLoginFlow.Page.OFFICIAL && !busy && pendingCallback == null && !completed)
                    return false;
                notice.setText("未対応の遷移を停止しました。");
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("http".equals(request.getUrl().getScheme()))
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                            java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (busy || completed || pendingCallback != null) return;
                pageFailed = false;
                notice.setText("公式認可ページを読み込み中…");
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!pageFailed && !busy && !completed && pendingCallback == null && url.equals(view.getUrl()))
                    notice.setText("公式ページで本人が承認してください。表示されない場合は再試行できます。");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !busy && !completed) failed("WebView通信エラー (" + error.getErrorCode() + ")");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && !busy && !completed) failed("WebView HTTP " + response.getStatusCode());
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                if (!busy && !completed) failed("TLS証明書エラー");
            }
        });
        layout.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        browser.loadUrl(start);
    }

    private void failed(String message) {
        pageFailed = true;
        notice.setText(message + "。再試行またはホームへ戻ってください。");
        retry.setEnabled(true);
    }

    private void exchange(String callback) {
        if (busy || completed || AimogeLoginFlow.page(callback) != AimogeLoginFlow.Page.CALLBACK) return;
        pendingCallback = callback;
        busy = true;
        retry.setEnabled(false);
        browser.stopLoading();
        browser.setVisibility(android.view.View.GONE);
        notice.setText("資格情報を交換中…");
        io.execute(() -> {
            int status = 0;
            String body = "";
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(callback).openConnection(Proxy.NO_PROXY);
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(25000);
                conn.setRequestMethod("GET");
                status = conn.getResponseCode();
                InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
                if (stream != null) try (InputStream in = stream) {
                    body = new String(AimogeWriteClient.bounded(in, 16000), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                // Delivery may be ambiguous: never automatically replay an exchange.
            } finally { if (conn != null) conn.disconnect(); }
            final int code = status;
            final boolean success = code == 200 && body.contains("Login complete. Return to futatabi.");
            final boolean again = AimogeLoginFlow.retryable(status, body);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                if (success) {
                    completed = true;
                    pendingCallback = null;
                    setResult(RESULT_OK);
                    finish();
                } else if (again) {
                    notice.setText("公式APIのDNS解決に失敗しました。通信復旧後、有効期限内に再試行してください。");
                    retry.setEnabled(true);
                } else {
                    pendingCallback = null;
                    completed = true;
                    notice.setText("資格情報交換を完了できませんでした (HTTP " + code + ")。ホームからログインをやり直してください。");
                }
            });
        });
    }

    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }
    @Override protected void onDestroy() {
        io.shutdownNow();
        pendingCallback = null;
        if (browser != null) {
            browser.stopLoading();
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
