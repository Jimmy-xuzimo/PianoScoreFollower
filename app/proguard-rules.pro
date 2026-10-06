# Keep WebView JS interface entry points reachable from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
