package com.dev.ytadblock.hooks;

import android.app.Activity;
import android.content.Context;
import android.os.PowerManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Keeps video/audio playing after the screen is locked or the app is sent
 * to the background (home button / recents / app switch).
 *
 * Why this approach instead of patching YouTube's own "is background
 * playback allowed" check: that check lives in obfuscated, renamed classes
 * that change every release, so hardcoding a patch there breaks on the next
 * YouTube update. android.app.Activity is a public, stable Android
 * framework class the app cannot rename, so hooking its lifecycle callbacks
 * keeps working across updates.
 *
 * What it does:
 * 1. No-ops Activity.onUserLeaveHint() and Activity.moveTaskToBack(boolean)
 *    for the YouTube app's own activities, so the app never receives the
 *    "user pressed home" signal that normally triggers it to pause
 *    playback and tear down the player.
 * 2. Holds a partial wake lock while the module is loaded so the CPU
 *    doesn't sleep and kill playback/decoding when the screen turns off.
 */
public class BackgroundPlaybackHook {

    private static PowerManager.WakeLock wakeLock;

    public static void init(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onUserLeaveHint",
                    XC_MethodReplacement.DO_NOTHING);

            XposedHelpers.findAndHookMethod(Activity.class, "moveTaskToBack", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    });

            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            acquireWakeLock((Activity) param.thisObject);
                        }
                    });

            XposedBridge.log("[YTAdBlock] Background playback hooks installed");
        } catch (Throwable t) {
            XposedBridge.log("[YTAdBlock] Failed to install background playback hooks: " + t);
        }
    }

    private static synchronized void acquireWakeLock(Activity activity) {
        if (wakeLock != null && wakeLock.isHeld()) return;
        try {
            PowerManager pm = (PowerManager) activity.getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "ytadblock:background-playback");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
            XposedBridge.log("[YTAdBlock] Wake lock acquired");
        } catch (Throwable t) {
            XposedBridge.log("[YTAdBlock] Failed to acquire wake lock: " + t);
        }
    }
}
