package com.example.module;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.InputConnection;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class MainModule extends XposedModule {

    private static final String TARGET_PACKAGE = "com.google.android.inputmethod.latin";
    private static final String TAG = "TextReplacer";
    private static final boolean VERBOSE = true;

    private static final String PREFS_NAME = "mod_prefs";
    private static final String WORD_MAP_KEY = "word_map";

    private final Set<Class<?>> hookedClasses = new HashSet<>();
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

        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
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
                    userDictionary.put(key.toLowerCase(), value);
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

    private void hookInputConnectionMethods(Class<?> icClass) {
        if (hookedClasses.contains(icClass)) {
            return;
        }

        hookedClasses.add(icClass);
        log(4, TAG, "Discovered real InputConnection class: " + icClass.getName());

        try {
            Method commitText = findMethodInHierarchy(icClass, "commitText", CharSequence.class, int.class);

            if (commitText != null) {
                log(4, TAG, "commitText found in " + commitText.getDeclaringClass().getName());

                hook(commitText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && args.size() >= 2 && args.get(0) instanceof CharSequence) {
                                    String typedText = args.get(0).toString();
                                    int newCursorPos = (Integer) args.get(1);

                                    if (VERBOSE) {
                                        log(4, TAG, "commitText: [" + typedText + "]");
                                    }

                                    // CASE 1: Complete word/chunk arrives at once
                                    String replacement = findReplacement(typedText);
                                    if (replacement != null) {
                                        String newText = replaceAllWords(typedText);
                                        lastComposingText = null;
                                        InputConnection ic = currentIC;

                                        if (ic != null) {
                                            ic.commitText(newText, newCursorPos);
                                            log(4, TAG, "Replaced chunk: [" + typedText + "] -> [" + newText + "]");
                                            return true;
                                        }
                                    }

                                    // CASE 2: Composing text fallback
                                    if (lastComposingText != null && containsDictionaryWord(lastComposingText) && typedText.length() <= 2) {
                                        InputConnection ic = currentIC;
                                        String composing = lastComposingText;
                                        lastComposingText = null;

                                        if (ic != null) {
                                            String badWord = findMatchingWord(composing);
                                            if (badWord != null) {
                                                String badReplacement = getReplacement(badWord);
                                                int deleteLen = composing.trim().length();

                                                ic.deleteSurroundingText(deleteLen, 0);
                                                ic.commitText(badReplacement + typedText, newCursorPos);
                                                log(4, TAG, "Replaced via composing fallback: " + badWord);
                                                return true;
                                            }
                                        }
                                    }

                                    // CASE 3: Character-by-character typing
                                    InputConnection ic = currentIC;
                                    if (ic != null) {
                                        int maxWordLength = getLongestWordLength();
                                        if (maxWordLength > 0) {
                                            CharSequence beforeCursor = ic.getTextBeforeCursor(maxWordLength, 0);
                                            if (beforeCursor != null) {
                                                String combined = beforeCursor.toString() + typedText;
                                                String matchingWord = findMatchingWordAtEnd(combined);

                                                if (matchingWord != null) {
                                                    String badReplacement = getReplacement(matchingWord);
                                                    int charsToDelete = matchingWord.length() - typedText.length();
                                                    if (charsToDelete < 0) {
                                                        charsToDelete = 0;
                                                    }

                                                    lastComposingText = null;
                                                    ic.deleteSurroundingText(charsToDelete, 0);
                                                    ic.commitText(badReplacement, newCursorPos);
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
                            return chain.proceed();
                        });
            } else {
                log(5, TAG, "commitText NOT FOUND in hierarchy of " + icClass.getName());
            }

            Method setComposingText = findMethodInHierarchy(icClass, "setComposingText", CharSequence.class, int.class);

            if (setComposingText != null) {
                log(4, TAG, "setComposingText found in " + setComposingText.getDeclaringClass().getName());

                hook(setComposingText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
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
                            return chain.proceed();
                        });
            } else {
                log(5, TAG, "setComposingText NOT FOUND in hierarchy of " + icClass.getName());
            }

        } catch (Throwable t) {
            log(6, TAG, "Failed to hook InputConnection methods on " + icClass.getName(), t);
        }
    }

    private String findReplacement(String text) {
        if (text == null) {
            return null;
        }

        String lowerText = text.toLowerCase();
        synchronized (userDictionary) {
            for (Map.Entry<String, String> entry : userDictionary.entrySet()) {
                String badWord = entry.getKey().toLowerCase();
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

        String lowerText = text.toLowerCase().trim();
        synchronized (userDictionary) {
            for (String word : userDictionary.keySet()) {
                if (lowerText.equals(word.toLowerCase())) {
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

        String lowerText = text.toLowerCase();
        synchronized (userDictionary) {
            String bestMatch = null;
            for (String word : userDictionary.keySet()) {
                String lowerWord = word.toLowerCase();
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
