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

    // Leave this on until you've confirmed it's working — it logs every
    // commitText / setComposingText call so you can see exactly what Gboard
    // is doing on your device. Flip to false once confirmed; it's noisy.
    private static final boolean VERBOSE = true;

    private final Set<Class<?>> hookedClasses = new HashSet<>();

    // Cached reference to the real InputConnection, refreshed every time
    // Gboard asks the framework for one. We call plain public InputConnection
    // methods directly on this when we need to edit text outside the args of
    // whatever call we're currently intercepting — no reflection needed since
    // InputConnection is a normal public interface.
    private volatile InputConnection currentIC = null;

    // The most recent text Gboard marked as "composing" (the underlined,
    // in-progress word). Some Gboard builds finalize a word through this
    // composing mechanism and only ever pass the triggering space/punctuation
    // to commitText — this lets us catch that case too.
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
                                if (args != null && !args.isEmpty()
                                        && args.get(0) instanceof CharSequence) {

                                    String typedText = args.get(0).toString();
                                    if (VERBOSE) log(4, TAG, "commitText: [" + typedText + "]");

                                    if (typedText.toLowerCase().contains("fuck")) {
                                        // Case 1: the bad word is right there in the
                                        // committed text (e.g. Gboard committed "fuck "
                                        // as a single call).
                                        String newText =
                                                typedText.replaceAll("(?i)fuck", "its a bad word");
                                        args.set(0, newText);
                                        lastComposingText = null;

                                    } else if (lastComposingText != null
                                            && lastComposingText.trim().equalsIgnoreCase("fuck")
                                            && typedText.length() <= 2) {
                                        // Case 2: the word was already finalized through
                                        // the composing mechanism, and this commitText
                                        // call is just the trailing space/punctuation
                                        // that triggered the finalize. Delete what Gboard
                                        // already placed in the field, then let this call
                                        // commit the replacement + whatever triggered it.
                                        InputConnection ic = currentIC;
                                        if (ic != null) {
                                            ic.deleteSurroundingText(
                                                    lastComposingText.trim().length(), 0);
                                            args.set(0, "its a bad word" + typedText);
                                            log(4, TAG, "Replaced via composing-text fallback");
                                        }
                                        lastComposingText = null;
                                        
                                    } else {
                                        // Case 3: Character-by-character typing (e.g., in Termux or search bars)
                                        InputConnection ic = currentIC;
                                        if (ic != null) {
                                            // Peek at the last 4 characters before the cursor
                                            CharSequence beforeCursor = ic.getTextBeforeCursor(4, 0);
                                            if (beforeCursor != null) {
                                                // Combine what's already in the field with the key just pressed
                                                String combined = beforeCursor.toString().toLowerCase() + typedText.toLowerCase();
                                                
                                                if (combined.endsWith("fuck")) {
                                                    // Calculate how many characters we need to delete from the screen.
                                                    // If typedText is "k" (1 char), we need to delete "fuc" (3 chars).
                                                    int charsToDelete = 4 - typedText.length();
                                                    
                                                    ic.deleteSurroundingText(charsToDelete, 0);
                                                    args.set(0, "its a bad word");
                                                    log(4, TAG, "Replaced via character-by-character fallback");
                                                    lastComposingText = null;
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, TAG, "Error inside commitText hook", t);
                            }
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

                                    // Track only — we deliberately don't rewrite this
                                    // one live, since doing so mid-word can jump the
                                    // cursor or fight with backspacing while the user
                                    // is still typing.
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

    // Walks up the class hierarchy to find exactly where a method is declared,
    // since the concrete class Gboard hands back is usually a subclass that
    // may or may not override each method itself.
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
