package com.bkfs.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    private static final String HOST = "bkfs-fresh-production.up.railway.app";
    private static final String BKFS_URL = "https://" + HOST + "/login";
    private WebView web;
    private ProgressBar progress;
    private View errorPanel;
    private ValueCallback<Uri[]> fileCallback;
    private boolean failed;

    private boolean internal(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }
    private void external(Uri uri) {
        if (!("tel".equals(uri.getScheme()) || ("https".equals(uri.getScheme())
            && ("wa.me".equals(uri.getHost()) || "api.whatsapp.com".equals(uri.getHost()))))) return;
        try { startActivity(new Intent("tel".equals(uri.getScheme()) ? Intent.ACTION_DIAL : Intent.ACTION_VIEW, uri)); }
        catch (android.content.ActivityNotFoundException e) { Toast.makeText(this,"Is action ke liye app nahi mila",Toast.LENGTH_LONG).show(); }
    }
    private void showError() { failed=true;progress.setVisibility(View.GONE);errorPanel.setVisibility(View.VISIBLE); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);setContentView(R.layout.activity_main);
        View container=findViewById(R.id.root);
        container.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });container.requestApplyInsets();
        web=findViewById(R.id.web);progress=findViewById(R.id.progress);errorPanel=findViewById(R.id.errorPanel);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(internal(request.getUrl()))return false;
                if(request.isForMainFrame())external(request.getUrl());return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){failed=false;errorPanel.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){progress.setVisibility(View.GONE);if(!failed)errorPanel.setVisibility(View.GONE);CookieManager.getInstance().flush();}
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error){if(req.isForMainFrame())showError();}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,WebResourceResponse response){if(req.isForMainFrame())showError();}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean userGesture,Message message){
                if(!userGesture)return false;
                WebView child=new WebView(MainActivity.this);
                child.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                        Uri uri=request.getUrl();if(internal(uri))web.loadUrl(uri.toString());else external(uri);child.destroy();return true;
                    }
                });
                ((WebView.WebViewTransport)message.obj).setWebView(child);message.sendToTarget();return true;
            }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                try{startActivityForResult(params.createIntent(),11);}catch(android.content.ActivityNotFoundException e){fileCallback.onReceiveValue(null);fileCallback=null;}return true;
            }
        });
        findViewById(R.id.retryButton).setOnClickListener(v->{errorPanel.setVisibility(View.GONE);web.loadUrl(BKFS_URL);});
        web.loadUrl(BKFS_URL);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==11&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    }
    @Override public void onBackPressed(){
        web.evaluateJavascript("(()=>{const panel=document.getElementById('fullpage');if(panel&&panel.classList.contains('on')&&typeof fullClose==='function'){fullClose();return true;}return false;})()",handled->{if(!"true".equals(handled)){if(web.canGoBack())web.goBack();else finish();}});
    }
    @Override protected void onDestroy(){if(fileCallback!=null)fileCallback.onReceiveValue(null);web.destroy();super.onDestroy();}
}
