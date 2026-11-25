package com.example.module;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.google.android.inputmethod.latin";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String TAG = "TextReplacer";
    private static final boolean VERBOSE = true;

    private static final String PREFS_NAME = "mod_prefs";
    private static final String WORD_MAP_KEY = "word_map";
    private static final String LOCK_PRIVATE_DNS_KEY = "lock_private_dns";
    private static final String DISALLOW_CONFIG_PRIVATE_DNS = "no_config_private_dns";

    private final Set<Class<?>> hookedClasses = new HashSet<>();
    private final ThreadLocal<Boolean> isInternalEdit = ThreadLocal.withInitial(() -> false);
    private volatile InputConnection currentIC = null;
    private volatile String lastComposingText = null;

    private final Map<String, String> userDictionary = new HashMap<>();
    private SharedPreferences remotePrefs;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(4, TAG, "Module loaded. Process: " + param.getProcessName());

        try {
            remotePrefs = getRemotePreferences(PREFS_NAME);
            loadDictionary();
        } catch (Throwable t) {
            log(6, TAG, "Failed to load remote preferences", t);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        String pkg = param.getPackageName();

        if (SETTINGS_PACKAGE.equals(pkg)) {
            log(4, TAG, "Module attached to Settings process");
            installPrivateDnsLockHooks(Settings.Global.class.getClassLoader());
            return;
        }

        if (!TARGET_PACKAGE.equals(pkg)) {
            return;
        }

        log(4, TAG, "Module attached to Gboard process");
        loadDictionary();

        try {
            Method getICMethod = InputMethodService.class.getDeclaredMethod("getCurrentInputConnection");

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

    private boolean isPrivateDnsLocked() {
        try {
            if (remotePrefs == null) {
                remotePrefs = getRemotePreferences(PREFS_NAME);
            }
            return remotePrefs != null && remotePrefs.getBoolean(LOCK_PRIVATE_DNS_KEY, false);
        } catch (Throwable t) {
            log(6, TAG, "Failed to read Private DNS lock state", t);
            return false;
        }
    }

    private boolean isPrivateDnsKey(Object arg) {
        if (!(arg instanceof String)) {
            return false;
        }
        return ((String) arg).toLowerCase(Locale.ROOT).contains("private_dns");
    }

    private void installPrivateDnsLockHooks(ClassLoader classLoader) {
        hookUserRestrictionMethod(UserManager.class, "hasUserRestriction", String.class);
        hookUserRestrictionMethod(UserManager.class, "hasUserRestriction", String.class, UserHandle.class);
        hookUserRestrictionMethod(UserManager.class, "hasBaseUserRestriction", String.class, UserHandle.class);

        hookSettingsPutMethods(Settings.Global.class);
        hookSettingsPutMethods(Settings.Secure.class);

        try {
            Class<?> csmClass = Class.forName("android.net.ConnectivitySettingsManager", false, classLoader);
            for (Method method : csmClass.getDeclaredMethods()) {
                String name = method.getName();
                if ("setPrivateDnsMode".equals(name) || "setPrivateDnsHostname".equals(name)) {
                    hook(method)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (isPrivateDnsLocked()) {
                                    log(4, TAG, "Blocked ConnectivitySettingsManager." + name);
                                    return null;
                                }
                                return chain.proceed();
                            });
                    log(4, TAG, "Hooked ConnectivitySettingsManager." + name);
                }
            }
        } catch (Throwable t) {
            log(5, TAG, "ConnectivitySettingsManager hooks skipped: " + t.getMessage());
        }
    }

    private void hookUserRestrictionMethod(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            hook(method)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (args != null && !args.isEmpty()
                                && DISALLOW_CONFIG_PRIVATE_DNS.equals(args.get(0))
                                && isPrivateDnsLocked()) {
                            return true;
                        }
                        return chain.proceed();
                    });
            log(4, TAG, "Hooked " + clazz.getSimpleName() + "." + methodName);
        } catch (Throwable t) {
            log(5, TAG, "Skipped " + clazz.getSimpleName() + "." + methodName + ": " + t.getMessage());
        }
    }

    private void hookSettingsPutMethods(Class<?> settingsTableClass) {
        for (Method method : settingsTableClass.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.startsWith("putString") && !name.startsWith("putInt")) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length >= 3 && params[0] == ContentResolver.class && params[1] == String.class) {
                try {
                    hook(method)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                List<Object> args = chain.getArgs();
                                if (args != null && args.size() >= 2
                                        && isPrivateDnsKey(args.get(1))
                                        && isPrivateDnsLocked()) {
                                    log(4, TAG, "Blocked " + settingsTableClass.getSimpleName() + "." + name + " for key: " + args.get(1));
                                    return false;
                                }
                                return chain.proceed();
                            });
                } catch (Throwable t) {
                    log(5, TAG, "Skipped " + settingsTableClass.getSimpleName() + "." + name + ": " + t.getMessage());
                }
            }
        }
        log(4, TAG, "Installed Private DNS write guards on " + settingsTableClass.getSimpleName());
    }

    private void loadDictionary() {
        try {
            if (remotePrefs == null) {
                remotePrefs = getRemotePreferences(PREFS_NAME);
            }

            String jsonString = remotePrefs.getString(WORD_MAP_KEY, "{}");
            JSONObject jsonObject = new JSONObject(jsonString);

            synchronized (userDictionary) {
                userDictionary.clear();
                Iterator<String> keys = jsonObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = jsonObject.getString(key);
                    if (key != null && !key.isEmpty()) {
                        userDictionary.put(key.toLowerCase(Locale.ROOT), value);
                    }
                }
            }

            log(4, TAG, "Loaded " + userDictionary.size() + " replacement words");

            if (VERBOSE) {
                synchronized (userDictionary) {
                    for (Map.Entry<String, String> entry : userDictionary.entrySet()) {
                        log(4, TAG, "Dictionary: [" + entry.getKey() + "] -> [" + entry.getValue() + "]");
                    }
                }
            }

        } catch (Throwable t) {
            log(6, TAG, "Failed to load dictionary", t);
        }
    }

    private InputConnection resolveInputConnection(XposedInterface.Chain chain) {
        Object thisObj = chain.getThisObject();
        if (thisObj instanceof InputConnection) {
            return (InputConnection) thisObj;
        }
        return currentIC;
    }

    private void hookInputConnectionMethods(Class<?> icClass) {
        if (hookedClasses.contains(icClass)) {
            return;
        }

        hookedClasses.add(icClass);
        log(4, TAG, "Discovered real InputConnection class: " + icClass.getName());

        try {
            hookCommitText(icClass);
            hookSetComposingText(icClass);
            hookFinishComposingText(icClass);
            hookDeleteSurroundingText(icClass, "deleteSurroundingText");
            hookDeleteSurroundingText(icClass, "deleteSurroundingTextInCodePoints");
            hookSendKeyEvent(icClass);
        } catch (Throwable t) {
            log(6, TAG, "Failed to hook InputConnection methods on " + icClass.getName(), t);
        }
    }

    private void hookCommitText(Class<?> icClass) {
        Method commitText = findMethodInHierarchy(icClass, "commitText", CharSequence.class, int.class);
        if (commitText == null) {
            log(5, TAG, "commitText NOT FOUND in hierarchy of " + icClass.getName());
            return;
        }

        log(4, TAG, "commitText found in " + commitText.getDeclaringClass().getName());

        hook(commitText)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(isInternalEdit.get())) {
                        return chain.proceed();
                    }

                    InputConnection ic = resolveInputConnection(chain);
                    try {
                        List<Object> args = chain.getArgs();
                        if (args != null && args.size() >= 2 && args.get(0) instanceof CharSequence) {
                            String typedText = args.get(0).toString();
                            int newCursorPos = (Integer) args.get(1);

                            if (VERBOSE) {
                                log(4, TAG, "commitText: [" + typedText + "]");
                            }

                            String replacement = findReplacement(typedText);
                            if (replacement != null && ic != null) {
                                String newText = replaceAllWords(typedText);
                                lastComposingText = null;
                                isInternalEdit.set(true);
                                try {
                                    ic.commitText(newText, newCursorPos);
                                } finally {
                                    isInternalEdit.set(false);
                                }
                                log(4, TAG, "Replaced chunk: [" + typedText + "] -> [" + newText + "]");
                                return true;
                            }

                            if (lastComposingText != null && containsDictionaryWord(lastComposingText) && typedText.length() <= 2) {
                                String composing = lastComposingText;
                                lastComposingText = null;

                                if (ic != null) {
                                    String badWord = findMatchingWord(composing);
                                    if (badWord != null) {
                                        String badReplacement = getReplacement(badWord);
                                        int deleteLen = composing.trim().length();

                                        isInternalEdit.set(true);
                                        try {
                                            ic.beginBatchEdit();
                                            try {
                                                ic.deleteSurroundingText(deleteLen, 0);
                                                ic.commitText(badReplacement + typedText, newCursorPos);
                                            } finally {
                                                ic.endBatchEdit();
                                            }
                                        } finally {
                                            isInternalEdit.set(false);
                                        }
                                        log(4, TAG, "Replaced via composing fallback: " + badWord);
                                        return true;
                                    }
                                }
                            }

                            if (ic != null) {
                                int maxWordLength = getLongestWordLength();
                                if (maxWordLength > 0) {
                                    CharSequence beforeCursor = ic.getTextBeforeCursor(maxWordLength, 0);
                                    if (beforeCursor != null) {
                                        String combined = beforeCursor.toString() + typedText;
                                        String matchingWord = findMatchingWordAtEnd(combined);

                                        if (matchingWord != null) {
                                            String badReplacement = getReplacement(matchingWord);
                                            int charsToDelete = Math.max(0, matchingWord.length() - typedText.length());

                                            lastComposingText = null;
                                            isInternalEdit.set(true);
                                            try {
                                                ic.beginBatchEdit();
                                                try {
                                                    ic.deleteSurroundingText(charsToDelete, 0);
                                                    ic.commitText(badReplacement, newCursorPos);
                                                } finally {
                                                    ic.endBatchEdit();
                                                }
                                            } finally {
                                                isInternalEdit.set(false);
                                            }
                                            log(4, TAG, "Replaced character-by-character: " + matchingWord + " -> " + badReplacement);
                                            return true;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        log(6, TAG, "Error inside commitText hook", t);
                    }

                    // CASE 4: Mid-word insertion (e.g., typing 'r' between 'b' and 'b')
                    Object result = chain.proceed();
                    try {
                        checkAndReplaceAroundCursor(ic);
                    } catch (Throwable t) {
                        log(6, TAG, "Error checking around cursor after commitText", t);
                    }
                    return result;
                });
    }

    private void hookSetComposingText(Class<?> icClass) {
        Method setComposingText = findMethodInHierarchy(icClass, "setComposingText", CharSequence.class, int.class);
        if (setComposingText == null) {
            log(5, TAG, "setComposingText NOT FOUND in hierarchy of " + icClass.getName());
            return;
        }

        log(4, TAG, "setComposingText found in " + setComposingText.getDeclaringClass().getName());

        hook(setComposingText)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(isInternalEdit.get())) {
                        return chain.proceed();
                    }

                    try {
                        List<Object> args = chain.getArgs();
                        if (args != null && !args.isEmpty() && args.get(0) instanceof CharSequence) {
                            String text = args.get(0).toString();
                            if (VERBOSE) {
                                log(4, TAG, "setComposingText: [" + text + "]");
                            }
                            lastComposingText = text;
                        }
                    } catch (Throwable t) {
                        log(6, TAG, "Error inside setComposingText hook", t);
                    }

                    Object result = chain.proceed();
                    try {
                        checkAndReplaceAroundCursor(resolveInputConnection(chain));
                    } catch (Throwable t) {
                        log(6, TAG, "Error checking around cursor after setComposingText", t);
                    }
                    return result;
                });
    }

    private void hookFinishComposingText(Class<?> icClass) {
        Method finishComposingText = findMethodInHierarchy(icClass, "finishComposingText");
        if (finishComposingText == null) {
            return;
        }

        log(4, TAG, "finishComposingText found in " + finishComposingText.getDeclaringClass().getName());

        hook(finishComposingText)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(isInternalEdit.get())) {
                        return chain.proceed();
                    }

                    Object result = chain.proceed();
                    lastComposingText = null;
                    try {
                        checkAndReplaceAroundCursor(resolveInputConnection(chain));
                    } catch (Throwable t) {
                        log(6, TAG, "Error checking around cursor after finishComposingText", t);
                    }
                    return result;
                });
    }

    private void hookDeleteSurroundingText(Class<?> icClass, String methodName) {
        Method deleteMethod = findMethodInHierarchy(icClass, methodName, int.class, int.class);
        if (deleteMethod == null) {
            return;
        }

        log(4, TAG, methodName + " found in " + deleteMethod.getDeclaringClass().getName());

        hook(deleteMethod)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(isInternalEdit.get())) {
                        return chain.proceed();
                    }

                    Object result = chain.proceed();
                    try {
                        checkAndReplaceAroundCursor(resolveInputConnection(chain));
                    } catch (Throwable t) {
                        log(6, TAG, "Error checking around cursor after " + methodName, t);
                    }
                    return result;
                });
    }

    private void hookSendKeyEvent(Class<?> icClass) {
        Method sendKeyEvent = findMethodInHierarchy(icClass, "sendKeyEvent", KeyEvent.class);
        if (sendKeyEvent == null) {
            return;
        }

        log(4, TAG, "sendKeyEvent found in " + sendKeyEvent.getDeclaringClass().getName());

        hook(sendKeyEvent)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (Boolean.TRUE.equals(isInternalEdit.get())) {
                        return chain.proceed();
                    }

                    Object result = chain.proceed();
                    try {
                        List<Object> args = chain.getArgs();
                        if (args != null && !args.isEmpty() && args.get(0) instanceof KeyEvent) {
                            KeyEvent event = (KeyEvent) args.get(0);
                            if (event.getAction() == KeyEvent.ACTION_DOWN
                                    && (event.getKeyCode() == KeyEvent.KEYCODE_DEL
                                    || event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL)) {
                                checkAndReplaceAroundCursor(resolveInputConnection(chain));
                            }
                        }
                    } catch (Throwable t) {
                        log(6, TAG, "Error checking around cursor after sendKeyEvent", t);
                    }
                    return result;
                });
    }

    /**
     * Scans text both before and after the cursor so that joining split text
     * (e.g., typing "br b" and deleting the space between 'r' and 'b', or inserting
     * a character in the middle of a word) still triggers replacement.
     */
    private boolean checkAndReplaceAroundCursor(InputConnection ic) {
        if (ic == null) {
            return false;
        }

        int maxWordLength = getLongestWordLength();
        if (maxWordLength <= 0) {
            return false;
        }

        CharSequence beforeSeq = ic.getTextBeforeCursor(maxWordLength, 0);
        CharSequence afterSeq = ic.getTextAfterCursor(maxWordLength, 0);
        String before = beforeSeq != null ? beforeSeq.toString() : "";
        String after = afterSeq != null ? afterSeq.toString() : "";

        if (before.isEmpty() && after.isEmpty()) {
            return false;
        }

        String combined = before + after;
        String lowerCombined = combined.toLowerCase(Locale.ROOT);
        int cursorIdx = before.length();

        String bestWord = null;
        String bestReplacement = null;
        int bestStart = -1;

        synchronized (userDictionary) {
            for (Map.Entry<String, String> entry : userDictionary.entrySet()) {
                String badWord = entry.getKey();
                if (badWord == null || badWord.isEmpty()) {
                    continue;
                }

                int searchFrom = Math.max(0, cursorIdx - badWord.length());
                int idx = lowerCombined.indexOf(badWord, searchFrom);
                while (idx != -1 && idx <= cursorIdx) {
                    int end = idx + badWord.length();
                    if (end >= cursorIdx) {
                        if (bestWord == null || badWord.length() > bestWord.length()) {
                            bestWord = badWord;
                            bestReplacement = entry.getValue();
                            bestStart = idx;
                        }
                        break;
                    }
                    idx = lowerCombined.indexOf(badWord, idx + 1);
                }
            }
        }

        if (bestWord == null || bestReplacement == null) {
            return false;
        }

        int deleteBefore = cursorIdx - bestStart;
        int deleteAfter = (bestStart + bestWord.length()) - cursorIdx;

        isInternalEdit.set(true);
        try {
            ic.beginBatchEdit();
            try {
                ic.finishComposingText();
                ic.deleteSurroundingText(deleteBefore, deleteAfter);
                ic.commitText(bestReplacement, 1);
            } finally {
                ic.endBatchEdit();
            }
            lastComposingText = null;
        } finally {
            isInternalEdit.set(false);
        }

        log(4, TAG, "Replaced around cursor: [" + bestWord + "] -> [" + bestReplacement + "]");
        return true;
    }

    private String findReplacement(String text) {
        if (text == null) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        synchronized (userDictionary) {
            for (Map.Entry<String, String> entry : userDictionary.entrySet()) {
                String badWord = entry.getKey().toLowerCase(Locale.ROOT);
                if (lowerText.contains(badWord)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String replaceAllWords(String text) {
        String result = text;
        synchronized (userDictionary) {
            for (Map.Entry<String, String> entry : userDictionary.entrySet()) {
                String badWord = entry.getKey();
                String replacement = entry.getValue();

                if (badWord == null || badWord.isEmpty()) {
                    continue;
                }

                result = result.replaceAll(
                        "(?i)" + java.util.regex.Pattern.quote(badWord),
                        java.util.regex.Matcher.quoteReplacement(replacement)
                );
            }
        }
        return result;
    }

    private boolean containsDictionaryWord(String text) {
        return findMatchingWord(text) != null;
    }

    private String findMatchingWord(String text) {
        if (text == null) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT).trim();
        synchronized (userDictionary) {
            for (String word : userDictionary.keySet()) {
                if (lowerText.equals(word.toLowerCase(Locale.ROOT))) {
                    return word;
                }
            }
        }
        return null;
    }

    private String findMatchingWordAtEnd(String text) {
        if (text == null) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        synchronized (userDictionary) {
            String bestMatch = null;
            for (String word : userDictionary.keySet()) {
                String lowerWord = word.toLowerCase(Locale.ROOT);
                if (lowerText.endsWith(lowerWord)) {
                    if (bestMatch == null || lowerWord.length() > bestMatch.length()) {
                        bestMatch = word;
                    }
                }
            }
            return bestMatch;
        }
    }

    private String getReplacement(String word) {
        synchronized (userDictionary) {
            return userDictionary.get(word);
        }
    }

    private int getLongestWordLength() {
        int longest = 0;
        synchronized (userDictionary) {
            for (String word : userDictionary.keySet()) {
                if (word != null) {
                    longest = Math.max(longest, word.length());
                }
            }
        }
        return longest;
    }

    private Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
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
