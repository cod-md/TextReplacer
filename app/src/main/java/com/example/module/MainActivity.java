package com.example.module;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "mod_prefs";
    private static final String WORD_MAP_KEY = "word_map";
    private static final String LOCK_PRIVATE_DNS_KEY = "lock_private_dns";
    private static final String REQUIRED_LINE_1 = "Wallahi i am fully aware of what i am doing";
    private static final String REQUIRED_LINE_2 = "And its not something that ruins my life";

    private SharedPreferences remotePrefs;
    private final HashMap<String, String> wordMap = new HashMap<>();

    private WordAdapter adapter;
    private Button lockPrivateDnsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        View lockContainer = findViewById(R.id.lockContainer);
        View mainContentContainer = findViewById(R.id.mainContentContainer);
        EditText lockInput = findViewById(R.id.lockInput);
        Button unlockButton = findViewById(R.id.unlockButton);

        lockInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                unlockButton.setEnabled(isUnlockPhraseValid(s != null ? s.toString() : ""));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        unlockButton.setOnClickListener(v -> {
            String entered = lockInput.getText() != null ? lockInput.getText().toString() : "";
            if (isUnlockPhraseValid(entered)) {
                lockContainer.setVisibility(View.GONE);
                mainContentContainer.setVisibility(View.VISIBLE);
            } else {
                lockInput.setError("Please type the exact required text");
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        Button addButton = findViewById(R.id.addButton);
        lockPrivateDnsButton = findViewById(R.id.lockPrivateDnsButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new WordAdapter(
                new ArrayList<>(),
                new WordAdapter.OnWordActionListener() {

                    @Override
                    public void onEdit(String badWord, String replacement) {
                        showWordDialog(badWord, replacement);
                    }

                    @Override
                    public void onDelete(String badWord) {
                        deleteWord(badWord);
                    }
                }
        );

        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> showWordDialog(null, null));
        lockPrivateDnsButton.setOnClickListener(v -> togglePrivateDnsLock());

        /*
         * Connect to LSPosed/libxposed service.
         */
        XposedServiceHelper.registerListener(
                new XposedServiceHelper.OnServiceListener() {

                    @Override
                    public void onServiceBind(XposedService service) {

                        runOnUiThread(() -> {

                            try {
                                remotePrefs =
                                        service.getRemotePreferences(PREFS_NAME);

                                loadWords();
                                updatePrivateDnsLockButton();

                            } catch (Exception e) {

                                Toast.makeText(
                                        MainActivity.this,
                                        "Failed to connect to LSPosed",
                                        Toast.LENGTH_LONG
                                ).show();

                                e.printStackTrace();
                            }
                        });
                    }

                    @Override
                    public void onServiceDied(XposedService service) {

                        runOnUiThread(() -> {

                            remotePrefs = null;

                            Toast.makeText(
                                    MainActivity.this,
                                    "LSPosed service disconnected",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }
                }
        );
    }

    private boolean isUnlockPhraseValid(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.replace("\r\n", "\n").replace("\r", "\n").trim();
        String[] lines = normalized.split("\n");
        if (lines.length != 2) {
            return false;
        }
        String line1 = lines[0].trim().replaceAll("\\s+", " ");
        String line2 = lines[1].trim().replaceAll("\\s+", " ");
        return line1.equalsIgnoreCase(REQUIRED_LINE_1)
                && line2.equalsIgnoreCase(REQUIRED_LINE_2);
    }

    private void updatePrivateDnsLockButton() {
        if (lockPrivateDnsButton == null) {
            return;
        }
        boolean isLocked = remotePrefs != null
                && remotePrefs.getBoolean(LOCK_PRIVATE_DNS_KEY, false);
        lockPrivateDnsButton.setText(
                isLocked ? "Private DNS: Locked (Tap to Unlock)" : "Lock Private DNS"
        );
    }

    private void togglePrivateDnsLock() {
        if (remotePrefs == null) {
            Toast.makeText(
                    this,
                    "Waiting for LSPosed service...",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        boolean currentlyLocked = remotePrefs.getBoolean(LOCK_PRIVATE_DNS_KEY, false);

        if (!currentlyLocked) {
            new AlertDialog.Builder(this)
                    .setTitle("Lock Private DNS?")
                    .setMessage(
                            "This will block changing Private DNS mode and Private DNS toggles in Android Settings.\n\nMake sure Settings (com.android.settings) is enabled in LSPosed scope for Text Replacer."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Activate Lock", (dialog, which) -> {
                        remotePrefs.edit()
                                .putBoolean(LOCK_PRIVATE_DNS_KEY, true)
                                .apply();
                        updatePrivateDnsLockButton();
                        Toast.makeText(
                                this,
                                "Private DNS locked in Settings",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Unlock Private DNS?")
                    .setMessage("Allow changing Private DNS settings again from Android Settings?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Unlock", (dialog, which) -> {
                        remotePrefs.edit()
                                .putBoolean(LOCK_PRIVATE_DNS_KEY, false)
                                .apply();
                        updatePrivateDnsLockButton();
                        Toast.makeText(
                                this,
                                "Private DNS unlocked",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show();
        }
    }

    private void loadWords() {

        if (remotePrefs == null) {
            return;
        }

        wordMap.clear();

        String jsonString =
                remotePrefs.getString(
                        WORD_MAP_KEY,
                        "{}"
                );

        try {

            JSONObject jsonObject =
                    new JSONObject(jsonString);

            Iterator<String> keys =
                    jsonObject.keys();

            while (keys.hasNext()) {

                String badWord = keys.next();

                String replacement =
                        jsonObject.getString(badWord);

                wordMap.put(
                        badWord,
                        replacement
                );
            }

        } catch (JSONException e) {

            e.printStackTrace();
        }

        refreshList();
    }

    private void showWordDialog(
            String badWord,
            String existingReplacement
    ) {

        if (remotePrefs == null) {

            Toast.makeText(
                    this,
                    "Waiting for LSPosed service...",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        android.view.View view =
                getLayoutInflater().inflate(
                        R.layout.dialog_add_word,
                        null
                );

        EditText wordInput =
                view.findViewById(R.id.wordInput);

        EditText replacementInput =
                view.findViewById(R.id.replacementInput);

        if (badWord != null) {

            wordInput.setText(badWord);

            replacementInput.setText(
                    existingReplacement
            );
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                badWord == null
                                        ? "Add Word"
                                        : "Edit Word"
                        )
                        .setView(view)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                badWord == null
                                        ? "Add"
                                        : "Save",
                                null
                        )
                        .create();

        dialog.setOnShowListener(
                d -> {

                    dialog.getButton(
                            AlertDialog.BUTTON_POSITIVE
                    ).setOnClickListener(v -> {

                        String newBadWord =
                                wordInput
                                        .getText()
                                        .toString()
                                        .trim();

                        String replacement =
                                replacementInput
                                        .getText()
                                        .toString();

                        if (newBadWord.isEmpty()) {

                            wordInput.setError(
                                    "Enter a word"
                            );

                            return;
                        }

                        if (replacement.isEmpty()) {

                            replacementInput.setError(
                                    "Enter a replacement"
                            );

                            return;
                        }

                        if (badWord != null
                                && !badWord.equals(newBadWord)) {

                            wordMap.remove(badWord);
                        }

                        wordMap.put(
                                newBadWord,
                                replacement
                        );

                        saveWords();

                        dialog.dismiss();
                    });
                }
        );

        dialog.show();
    }

    private void deleteWord(String badWord) {

        if (remotePrefs == null) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete word?")
                .setMessage(
                        "\"" + badWord +
                                "\" will be removed."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Delete",
                        (dialog, which) -> {

                            wordMap.remove(
                                    badWord
                            );

                            saveWords();
                        }
                )
                .show();
    }

    private void saveWords() {

        if (remotePrefs == null) {

            Toast.makeText(
                    this,
                    "LSPosed service unavailable",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            JSONObject jsonObject =
                    new JSONObject();

            for (Map.Entry<String, String> entry :
                    wordMap.entrySet()) {

                jsonObject.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }

            remotePrefs.edit()
                    .putString(
                            WORD_MAP_KEY,
                            jsonObject.toString()
                    )
                    .apply();

            refreshList();

            Toast.makeText(
                    this,
                    "Saved",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (JSONException e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "Failed to save",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void refreshList() {

        if (adapter == null) {
            return;
        }

        ArrayList<String> badWords =
                new ArrayList<>(wordMap.keySet());

        badWords.sort(String.CASE_INSENSITIVE_ORDER);

        adapter.updateData(
                badWords,
                wordMap
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
