package com.example.module;

import android.content.SharedPreferences;
import android.os.Bundle;
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

    private SharedPreferences remotePrefs;

    private final HashMap<String, String> wordMap = new HashMap<>();

    private WordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        Button addButton = findViewById(R.id.addButton);

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

    /**
     * Load the dictionary from RemotePreferences.
     */
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

    /**
     * Add or edit a word.
     *
     * If badWord is null -> new word.
     * Otherwise -> editing existing word.
     */
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

        /*
         * Editing existing word.
         */
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

                        /*
                         * Validation.
                         */
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

                        /*
                         * If editing and the user changed
                         * the original bad word, remove
                         * the old entry first.
                         */
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

    /**
     * Delete a word.
     */
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

    /**
     * Save the entire dictionary to RemotePreferences.
     */
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

    /**
     * Refresh RecyclerView.
     */
    private void refreshList() {

        if (adapter == null) {
            return;
        }

        ArrayList<String> badWords =
                new ArrayList<>(wordMap.keySet());

        /*
         * Sort alphabetically.
         */
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
