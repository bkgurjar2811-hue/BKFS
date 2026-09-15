package in.bkfs.demo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingBackup;
    private static final int SAVE_BACKUP = 10, OPEN_BACKUP = 11;
    private static final String ORIGIN = "https://appassets.androidplatform.net/";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) pendingBackup = state.getString("pendingBackup");
        web = new WebView(this);
        setContentView(web);
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // user-selected JSON import only
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = loader.shouldInterceptRequest(request.getUrl());
                return response != null ? response : new WebResourceResponse("text/plain", "UTF-8", 403,
                    "Blocked", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().equals(ORIGIN + "assets/index.html");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try { startActivityForResult(intent, OPEN_BACKUP); }
                catch (android.content.ActivityNotFoundException error) {
                    fileCallback.onReceiveValue(null); fileCallback = null; show("No file picker available");
                }
                return true;
            }
        });
        // Only APK-bundled content can load in this WebView. The bridge cannot read files.
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void saveBackup(String json) {
                if (json == null || json.length() > 5_000_000) { show("Backup too large"); return; }
                try { new JSONObject(json); } catch (Exception e) { show("Invalid backup"); return; }
                runOnUiThread(() -> {
                    if (pendingBackup != null) { show("Finish the current save first"); return; }
                    pendingBackup = json;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "BKFS-backup.json");
                    try { startActivityForResult(intent, SAVE_BACKUP); }
                    catch (android.content.ActivityNotFoundException e) { pendingBackup = null; show("No file picker available"); }
                });
            }
        }, "BKFSAndroid");
        web.loadUrl(ORIGIN + "assets/index.html");
    }
    private void show(String message) { runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OPEN_BACKUP && fileCallback != null) {
            fileCallback.onReceiveValue(result == RESULT_OK && data != null && data.getData() != null
                ? new Uri[]{data.getData()} : null);
            fileCallback = null;
        }
        if (request == SAVE_BACKUP) {
            String json = pendingBackup; pendingBackup = null;
            if (result != RESULT_OK || data == null || data.getData() == null || json == null) return;
            try (OutputStream stream = getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (stream == null) throw new java.io.IOException("No output stream");
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                show("Backup saved");
            } catch (Exception e) { show("Backup could not be saved"); }
        }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("(()=>{if(!document.querySelector('#modal').classList.contains('hidden')){closeModal();return true;}if(document.querySelector('#sidebar').classList.contains('open')){toggleMenu();return true;}if(session&&page!=='dashboard'){go('dashboard');return true;}return false;})()",
            handled -> { if (!"true".equals(handled)) finish(); });
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (pendingBackup != null && pendingBackup.length() < 100_000) state.putString("pendingBackup", pendingBackup);
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        web.removeJavascriptInterface("BKFSAndroid"); web.destroy(); super.onDestroy();
    }
}
