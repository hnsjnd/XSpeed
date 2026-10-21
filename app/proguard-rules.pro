# XSpeed — keep WebView / JS bridge if minify is later enabled
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
