package com.example.module;

import android.inputmethodservice.InputMethodService;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.google.android.inputmethod.latin";
    
    // Keep track of classes we've already hooked so we don't hook them repeatedly on every keystroke
    private final Set<Class<?>> hookedClasses = new HashSet<>();

    // Constructor removed to fix Error #1: XposedModule requires no arguments

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        try {
            // 1. Hook the base framework method to discover the real class at runtime
            Method getICMethod = InputMethodService.class.getDeclaredMethod("getCurrentInputConnection");

            hook(getICMethod)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        
                        if (result != null) {
                            Class<?> runtimeClass = result.getClass();
                            // Pass the discovered class to our dynamic hooker
                            hookInputConnectionMethods(runtimeClass);
                        }
                        
                        return result;
                    });

            log(4, "TextReplacer", "Installed discovery hook on getCurrentInputConnection");

        } catch (Throwable t) {
            log(6, "TextReplacer", "Failed to install discovery hook", t);
        }
    }

    private void hookInputConnectionMethods(Class<?> icClass) {
        // Only hook each discovered class once
        if (hookedClasses.contains(icClass)) {
            return;
        }
        hookedClasses.add(icClass);

        log(4, "TextReplacer", "Discovered real InputConnection class: " + icClass.getName());

        try {
            // 2. Find and hook commitText in the class hierarchy
            Method commitText = findMethodInHierarchy(icClass, "commitText", CharSequence.class, int.class);
            if (commitText != null) {
                hook(commitText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> { // Inline lambda fixes Error #2
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()) {
                                    Object firstArg = args.get(0);
                                    if (firstArg instanceof CharSequence) {
                                        String typedText = firstArg.toString();
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            String newText = typedText.replaceAll("(?i)fuck", "its a bad word");
                                            args.set(0, newText);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, "TextReplacer", "Error inside commitText hook", t);
                            }
                            return chain.proceed();
                        });
                log(4, "TextReplacer", "Hooked commitText on " + commitText.getDeclaringClass().getName());
            }

            // 3. Find and hook setComposingText in the class hierarchy
            Method setComposingText = findMethodInHierarchy(icClass, "setComposingText", CharSequence.class, int.class);
            if (setComposingText != null) {
                hook(setComposingText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()) {
                                    Object firstArg = args.get(0);
                                    if (firstArg instanceof CharSequence) {
                                        String typedText = firstArg.toString();
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            String newText = typedText.replaceAll("(?i)fuck", "its a bad word");
                                            args.set(0, newText);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, "TextReplacer", "Error inside setComposingText hook", t);
                            }
                            return chain.proceed();
                        });
                log(4, "TextReplacer", "Hooked setComposingText on " + setComposingText.getDeclaringClass().getName());
            }

        } catch (Throwable t) {
            log(6, "TextReplacer", "Failed to dynamically hook methods for " + icClass.getName(), t);
        }
    }

    // Helper method to walk up the class hierarchy to find exactly where the method is declared
    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass(); // Check the parent class if not found here
            }
        }
        return null;
    }
}
