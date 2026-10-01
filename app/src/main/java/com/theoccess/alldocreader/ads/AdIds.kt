package com.theoccess.alldocreader.ads

/**
 * AdMob ad unit IDs.
 *
 * These are Google's official TEST IDs: they always fill with "Test Ad" creatives and never
 * earn money, so they are safe while developing. Before publishing, create your own units in
 * the AdMob console (Apps → your app → Ad units) and paste their IDs here:
 *  - BANNER: a "Banner" unit (the ad bar above the bottom tabs, under file lists and the reader);
 *  - INTERSTITIAL: an "Interstitial" unit (after converting / saving, when opening a category, app start);
 *  - APP_OPEN: an "App open" unit (the "Continue to app ›" ad at app start / Welcome back).
 * Also replace the app ID (…~…) in AndroidManifest.xml. Never click your own live ads.
 */
object AdIds {
    const val BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
