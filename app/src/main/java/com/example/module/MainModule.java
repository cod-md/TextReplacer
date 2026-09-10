package com.example.module;

import android.view.inputmethod.InputConnectionWrapper;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE =
            "com.google.android.inputmethod.latin";

    @Override
    public void onPackageLoaded(
            XposedModuleInterface.PackageLoadedParam param) {

        super.onPackageLoaded(param);

        // Only run inside Gboard
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            Method commitTextMethod =
                    InputConnectionWrapper.class.getDeclaredMethod(
                            "commitText",
                            CharSequence.class,
                            int.class
                    );

            hook(commitTextMethod)
                    .setExceptionMode(
                            XposedInterface.ExceptionMode.PROTECTIVE
                    )
                    .intercept(chain -> {

                        List<Object> args = chain.getArgs();

                        if (args != null && !args.isEmpty()) {

                            Object firstArg = args.get(0);

                            if (firstArg instanceof CharSequence) {

                                String typedText =
                                        firstArg.toString();

                                if (typedText
                                        .toLowerCase()
                                        .contains("fuck")) {

                                    String newText =
                                            typedText.replaceAll(
                                                    "(?i)fuck",
                                                    "its a bad word"
                                            );

                                    // Replace commitText's first argument
                                    args.set(0, newText);
                                }
                            }
                        }

                        // Continue to the original method
                        return chain.proceed();
                    });

            log(
                    4,
                    "TextReplacer",
                    "Gboard commitText hook installed."
            );

        } catch (Throwable t) {

            log(
                    6,
                    "TextReplacer",
                    "Failed to install commitText hook.",
                    t
            );
        }
    }
}
