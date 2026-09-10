package com.example.module;

import android.view.inputmethod.InputConnectionWrapper;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.XposedHooker;

public class MainModule extends XposedModule {

    public MainModule() {
        super();
    }

    @Override
    public void onPackageLoaded(
            XposedInterface.PackageLoadedParam param
    ) {
        super.onPackageLoaded(param);

        // Only hook Gboard.
        if (!"com.google.android.inputmethod.latin".equals(param.getPackageName())) {
            return;
        }

        // Avoid installing the hook more than once.
        if (!param.isFirstPackage()) {
            return;
        }

        try {
            Method commitText = InputConnectionWrapper.class.getDeclaredMethod(
                    "commitText",
                    CharSequence.class,
                    int.class
            );

            hook(commitText)
                    .setExceptionMode(
                            XposedInterface.ExceptionMode.PROTECTIVE
                    )
                    .intercept(TextReplacementHooker.class);

            log("Gboard commitText hook installed.");

        } catch (NoSuchMethodException e) {
            log("commitText() not found: " + e);
        } catch (Throwable t) {
            log("Failed to install hook: " + t);
        }
    }

    @XposedHooker
    public static class TextReplacementHooker
            implements XposedInterface.Hooker {

        public static Object intercept(
                XposedInterface.Chain<?> chain
        ) throws Throwable {

            Object[] args = chain.getArgs().toArray();

            /*
             * commitText(
             *     CharSequence text,
             *     int newCursorPosition
             * )
             */

            if (args.length >= 1 && args[0] instanceof CharSequence) {

                String typedText = args[0].toString();

                // Case-insensitive replacement.
                String newText = typedText.replaceAll(
                        "(?i)fuck",
                        "its a bad word"
                );

                if (!typedText.equals(newText)) {
                    args[0] = newText;
                }
            }

            // Continue to the original method.
            return chain.proceed(args);
        }
    }
}
