package com.example.module;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.google.android.inputmethod.latin";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        // Only run inside Gboard
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            // 1. Target the core method Gboard uses to get the text field connection
            Method getICMethod = InputMethodService.class.getDeclaredMethod("getCurrentInputConnection");

            hook(getICMethod)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        
                        // Let the original method run first to get the REAL InputConnection
                        Object result = chain.proceed();

                        if (result instanceof InputConnection) {
                            InputConnection originalIc = (InputConnection) result;

                            // 2. Wrap it with our own InputConnectionWrapper and return it to Gboard
                            return new InputConnectionWrapper(originalIc, true) {
                                
                                @Override
                                public boolean commitText(CharSequence text, int newCursorPosition) {
                                    if (text != null) {
                                        String typedText = text.toString();
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            text = typedText.replaceAll("(?i)fuck", "its a bad word");
                                        }
                                    }
                                    return super.commitText(text, newCursorPosition);
                                }

                                // 3. Catch composing text as well! 
                                // Gboard uses this constantly while typing before hitting space.
                                @Override
                                public boolean setComposingText(CharSequence text, int newCursorPosition) {
                                    if (text != null) {
                                        String typedText = text.toString();
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            text = typedText.replaceAll("(?i)fuck", "its a bad word");
                                        }
                                    }
                                    return super.setComposingText(text, newCursorPosition);
                                }
                            };
                        }

                        // Fallback if result isn't an InputConnection
                        return result;
                    });

            log(4, "TextReplacer", "Gboard InputConnection hook installed.");

        } catch (Throwable t) {
            log(6, "TextReplacer", "Failed to install InputConnection hook.", t);
        }
    }
}
