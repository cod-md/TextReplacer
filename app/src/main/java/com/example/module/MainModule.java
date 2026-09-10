package com.example.module;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputConnection;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.google.android.inputmethod.latin";
    private static final String TAG = "TextReplacer";

    private static final boolean VERBOSE = true;

    private final Set<Class<?>> hookedClasses = new HashSet<>();

    private volatile InputConnection currentIC = null;
    private volatile String lastComposingText = null;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        log(4, TAG, "Module attached to Gboard process");

        try {
            Method getICMethod =
                    InputMethodService.class.getDeclaredMethod("getCurrentInputConnection");

            hook(getICMethod)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (result instanceof InputConnection) {
                            currentIC = (InputConnection) result;
                            hookInputConnectionMethods(result.getClass());
                        }
                        return result;
                    });

            log(4, TAG, "Installed discovery hook on getCurrentInputConnection");

        } catch (Throwable t) {
            log(6, TAG, "Failed to install discovery hook", t);
        }
    }

    private void hookInputConnectionMethods(Class<?> icClass) {
        if (hookedClasses.contains(icClass)) {
            return;
        }
        hookedClasses.add(icClass);

        log(4, TAG, "Discovered real InputConnection class: " + icClass.getName());

        try {
            Method commitText =
                    findMethodInHierarchy(icClass, "commitText", CharSequence.class, int.class);

            if (commitText != null) {
                log(4, TAG, "commitText found in " + commitText.getDeclaringClass().getName());

                hook(commitText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && args.size() >= 2
                                        && args.get(0) instanceof CharSequence) {

                                    String typedText = args.get(0).toString();
                                    int newCursorPos = (Integer) args.get(1);

                                    if (VERBOSE) log(4, TAG, "commitText: [" + typedText + "]");

                                    if (typedText.toLowerCase().contains("fuck")) {
                                        // Case 1: Word committed as a single chunk
                                        String newText =
                                                typedText.replaceAll("(?i)fuck", "its a bad word");
                                        lastComposingText = null;
                                        
                                        if (currentIC != null) {
                                            // Call commitText manually with our modified string
                                            currentIC.commitText(newText, newCursorPos);
                                            // Return true to cancel the original call
                                            return true;
                                        }

                                    } else if (lastComposingText != null
                                            && lastComposingText.trim().equalsIgnoreCase("fuck")
                                            && typedText.length() <= 2) {
                                        // Case 2: Composing text fallback
                                        InputConnection ic = currentIC;
                                        int deleteLen = lastComposingText.trim().length();
                                        lastComposingText = null;
                                        
                                        if (ic != null) {
                                            ic.deleteSurroundingText(deleteLen, 0);
                                            ic.commitText("its a bad word" + typedText, newCursorPos);
                                            log(4, TAG, "Replaced via composing-text fallback");
                                            return true;
                                        }

                                    } else {
                                        // Case 3: Character-by-character typing (Termux)
                                        InputConnection ic = currentIC;
                                        if (ic != null) {
                                            CharSequence beforeCursor = ic.getTextBeforeCursor(4, 0);
                                            if (beforeCursor != null) {
                                                String combined = beforeCursor.toString().toLowerCase() 
                                                        + typedText.toLowerCase();
                                                
                                                if (combined.endsWith("fuck")) {
                                                    int charsToDelete = 4 - typedText.length();
                                                    lastComposingText = null;
                                                    
                                                    ic.deleteSurroundingText(charsToDelete, 0);
                                                    ic.commitText("its a bad word", newCursorPos);
                                                    log(4, TAG, "Replaced via character-by-character fallback");
                                                    return true; 
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, TAG, "Error inside commitText hook", t);
                            }
                            
                            // If we didn't find the bad word, let the original character commit normally
                            return chain.proceed();
                        });
            } else {
                log(5, TAG, "commitText NOT FOUND in hierarchy of " + icClass.getName());
            }

            Method setComposingText = findMethodInHierarchy(
                    icClass, "setComposingText", CharSequence.class, int.class);

            if (setComposingText != null) {
                log(4, TAG, "setComposingText found in "
                        + setComposingText.getDeclaringClass().getName());

                hook(setComposingText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()
                                        && args.get(0) instanceof CharSequence) {

                                    String text = args.get(0).toString();
                                    if (VERBOSE) log(4, TAG, "setComposingText: [" + text + "]");
                                    lastComposingText = text;
                                }
                            } catch (Throwable t) {
                                log(6, TAG, "Error inside setComposingText hook", t);
                            }
                            return chain.proceed();
                        });
            } else {
                log(5, TAG, "setComposingText NOT FOUND in hierarchy of " + icClass.getName());
            }

        } catch (Throwable t) {
            log(6, TAG, "Failed to hook InputConnection methods on " + icClass.getName(), t);
        }
    }

    private Method findMethodInHierarchy(
            Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
