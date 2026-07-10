package com.dev.ytadblock;

import com.dev.ytadblock.hooks.AdBlockHook;
import com.dev.ytadblock.hooks.BackgroundPlaybackHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TARGET_PKG = "com.google.android.youtube";

    @Override
    public void initZygote(StartupParam startupParam) {
        XposedBridge.log("[YTAdBlock] initZygote - module loaded");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("[YTAdBlock] handleLoadPackage - hooking YouTube");
        AdBlockHook.init(lpparam.classLoader);
        BackgroundPlaybackHook.init(lpparam.classLoader);
    }
}
