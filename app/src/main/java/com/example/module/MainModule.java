package com.example.module;

import android.view.inputmethod.InputConnectionWrapper;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.annotations.XposedHooker;

public class MainModule extends XposedModule {

    public MainModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        
        // Prevent hooking the same method multiple times per app
        if (!param.isFirstPackage()) return;

        try {
            // Target the keyboard's commitText method (triggered when you hit space on a word)
            Method commitTextMethod = InputConnectionWrapper.class.getDeclaredMethod("commitText", CharSequence.class, int.class);
            hook(commitTextMethod, TextReplacementHooker.class);
        } catch (NoSuchMethodException e) {
            // Method not found on this specific Android version, ignore safely
        }
    }

    // Modern libxposed hook structure
    @XposedHooker
    public static class TextReplacementHooker implements XposedInterface.Hooker {
        public static void before(XposedInterface.BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            
            // Ensure arguments exist and the first argument is the typed text
            if (args != null && args.length > 0 && args[0] instanceof CharSequence) {
                String typedText = args[0].toString();
                
                // Case-insensitive check for your target word
                if (typedText.toLowerCase().contains("fuck")) {
                    // Replace the word before it is committed to the text field
                    String newText = typedText.replaceAll("(?i)fuck", "its a bad word");
                    args[0] = newText;
                }
            }
        }
    }
}
