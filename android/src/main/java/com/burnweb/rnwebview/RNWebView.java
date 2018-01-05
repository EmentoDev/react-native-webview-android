package com.burnweb.rnwebview;

import android.annotation.SuppressLint;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.widget.FrameLayout;

import com.facebook.react.common.SystemClock;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;

class RNWebView extends WebView implements LifecycleEventListener {

    private final EventDispatcher mEventDispatcher;
    private final RNWebViewManager mViewManager;

    private String charset = "UTF-8";
    private String baseUrl = "file:///";
    private String injectedJavaScript = null;
    private boolean allowUrlRedirect = false;

    private Activity mActivity;
    private View mWebView;
    private View mVideoView;
    private ThemedReactContext mContext;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private FrameLayout mFullscreenContainer;

    private final FrameLayout.LayoutParams FULLSCREEN_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

    protected static final String BRIDGE_NAME = "__REACT_WEB_VIEW_BRIDGE";

    protected class ReactWebViewBridge {
        RNWebView mContext;

        ReactWebViewBridge(RNWebView c) {
            mContext = c;
        }

        @JavascriptInterface
        public void postMessage(String message) {
            mContext.onMessage(message);
        }
    }

    protected ReactWebViewBridge createReactWebViewBridge(RNWebView webView) {
        return new ReactWebViewBridge(webView);
    }

    public void linkBridge() {
        loadUrl("javascript:(" +
                "window.originalPostMessage = window.postMessage;" +
                "window.postMessage = function(data, origin) {" +
                "if(data && data.data && data.data.includes('Meteor._setImmediate')){" +
                "window.originalPostMessage(JSON.stringify(data), origin)" +
                "} else {" +
                BRIDGE_NAME + ".postMessage(JSON.stringify(data));" +
                "}" +
                "}" +
                ")");
    }

    protected class EventWebClient extends WebViewClient {
        public boolean shouldOverrideUrlLoading(WebView view, String url){
            if(!url.startsWith("http") && !url.startsWith("about") && !url.startsWith("file:")){
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    System.err.println(ex.getMessage());
                }

                return true;
            }

            if(RNWebView.this.getAllowUrlRedirect()) {
                // do your handling codes here, which url is the requested url
                // probably you need to open that url rather than redirect:
                view.loadUrl(url);

                return false; // then it is not handled by default action
            }

            return super.shouldOverrideUrlLoading(view, url);
        }

        public void onPageFinished(WebView view, String url) {
            mEventDispatcher.dispatchEvent(new NavigationStateChangeEvent(getId(), SystemClock.nanoTime(), view.getTitle(), false, url, view.canGoBack(), view.canGoForward()));

            if(RNWebView.this.getInjectedJavaScript() != null) {
                view.loadUrl("javascript:(function() {\n" + RNWebView.this.getInjectedJavaScript() + ";\n})();");
            }

            linkBridge();
        }

        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            mEventDispatcher.dispatchEvent(new NavigationStateChangeEvent(getId(), SystemClock.nanoTime(), view.getTitle(), true, url, view.canGoBack(), view.canGoForward()));
        }
    }

    protected class CustomWebChromeClient extends WebChromeClient {
        public CustomWebChromeClient(Activity activity, WebView webView) {
            mActivity = activity;
            mWebView = webView;
        }

        public CustomWebChromeClient() {}

        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            getModule().showAlert(url, message, result);
            return true;
        }

        // For Android 4.1+
        @SuppressWarnings("unused")
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
            getModule().startFileChooserIntent(uploadMsg, acceptType);
        }

        // For Android 5.0+
        @SuppressLint("NewApi")
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            return getModule().startFileChooserIntent(filePathCallback, fileChooserParams.createIntent());
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (mVideoView != null) {
                callback.onCustomViewHidden();
                return;
            }

            mVideoView = view;
            mCustomViewCallback = callback;

            FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE);
            mFullscreenContainer = new FullscreenHolder(mActivity);
            mFullscreenContainer.addView(view, FULLSCREEN_LAYOUT_PARAMS);
            decor.addView(mFullscreenContainer, FULLSCREEN_LAYOUT_PARAMS);

            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }

        @Override
        public void onHideCustomView() {
            if (mVideoView == null) return;

            FrameLayout decor = (FrameLayout) mActivity.getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            decor.removeView(mFullscreenContainer);
            mFullscreenContainer = null;
            mVideoView = null;
            mCustomViewCallback.onCustomViewHidden();
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    protected class GeoWebChromeClient extends CustomWebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, true, false);
        }
    }

    public RNWebView(RNWebViewManager viewManager, ThemedReactContext reactContext) {
        super(reactContext);

        mContext = reactContext;

        mViewManager = viewManager;
        mEventDispatcher = reactContext.getNativeModule(UIManagerModule.class).getEventDispatcher();

        this.getSettings().setJavaScriptEnabled(true);
        this.getSettings().setBuiltInZoomControls(false);
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setGeolocationEnabled(false);
        this.getSettings().setPluginState(WebSettings.PluginState.ON);
        this.getSettings().setAllowFileAccess(true);
        this.getSettings().setAllowFileAccessFromFileURLs(true);
        this.getSettings().setAllowUniversalAccessFromFileURLs(true);
        this.getSettings().setLoadsImagesAutomatically(true);
        this.getSettings().setBlockNetworkImage(false);
        this.getSettings().setBlockNetworkLoads(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        this.setWebViewClient(new EventWebClient());
        this.setWebChromeClient(getCustomClient());

        // Add javascript interface
        this.addJavascriptInterface(createReactWebViewBridge(this), BRIDGE_NAME);
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getCharset() {
        return this.charset;
    }

    public void setAllowUrlRedirect(boolean a) {
        this.allowUrlRedirect = a;
    }

    public boolean getAllowUrlRedirect() {
        return this.allowUrlRedirect;
    }

    public void setInjectedJavaScript(String injectedJavaScript) {
        this.injectedJavaScript = injectedJavaScript;
    }

    public String getInjectedJavaScript() {
        return this.injectedJavaScript;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public CustomWebChromeClient getCustomClient() {
        return new CustomWebChromeClient(this.mContext.getCurrentActivity(), this);
    }

    public GeoWebChromeClient getGeoClient() {
        return new GeoWebChromeClient();
    }

    public RNWebViewModule getModule() {
        return mViewManager.getPackage().getModule();
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        destroy();
    }

    @Override
    public void onDetachedFromWindow() {
        this.loadDataWithBaseURL(this.getBaseUrl(), "<html></html>", "text/html", this.getCharset(), null);
        super.onDetachedFromWindow();
    }

    @JavascriptInterface
    public void onMessage(String jsParamaters) {
        mEventDispatcher.dispatchEvent(new OnMessageEvent(getId(), jsParamaters));
    }

    static class FullscreenHolder extends FrameLayout {

        public FullscreenHolder(Context ctx) {
            super(ctx);
            setBackgroundColor(ctx.getResources().getColor(android.R.color.black));
        }

        @Override
        public boolean onTouchEvent(MotionEvent evt) {
            return true;
        }

    }
}
