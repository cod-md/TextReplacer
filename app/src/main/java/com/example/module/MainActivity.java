package com.example.module;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends Activity {

    // Our word dictionary
    private Map<String, String> userDictionary = new HashMap<>();

    // Storage
    private SharedPreferences prefs;

    // RecyclerView
    private WordAdapter adapter;
    private ArrayList<String> wordList;

    // Used when editing an existing word
    private String wordBeingEdited = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get SharedPreferences
        prefs = getSharedPreferences("mod_prefs", Context.MODE_PRIVATE);

        // Load saved words
        loadExistingWords();

        // Find views
        EditText badWordInput = findViewById(R.id.input_bad_word);
        EditText replacementInput = findViewById(R.id.input_replacement);
        Button saveButton = findViewById(R.id.btn_save);

        RecyclerView recyclerView = findViewById(R.id.recycler_words);

        // Create list from dictionary keys
        wordList = new ArrayList<>(userDictionary.keySet());

        // Setup RecyclerView
        adapter = new WordAdapter(
                wordList,
                userDictionary,

                // EDIT callback
                word -> {
                    String replacement = userDictionary.get(word);

                    badWordInput.setText(word);
                    replacementInput.setText(replacement);

                    wordBeingEdited = word;

                    saveButton.setText("Update");
                },

                // DELETE callback
                word -> {
                    userDictionary.remove(word);
                    wordList.remove(word);

                    saveWordsToStorage();
                    adapter.notifyDataSetChanged();

                    Toast.makeText(
                            this,
                            "Deleted: " + word,
                            Toast.LENGTH_SHORT
                    ).show();
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // SAVE / UPDATE button
        saveButton.setOnClickListener(v -> {

            String badWord = badWordInput
                    .getText()
                    .toString()
                    .trim()
                    .toLowerCase();

            String replacement = replacementInput
                    .getText()
                    .toString()
                    .trim();

            if (badWord.isEmpty()) {
                Toast.makeText(
                        this,
                        "Enter a word",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            // EDITING an existing word
            if (wordBeingEdited != null) {

                // Remove old key if the user changed the word
                userDictionary.remove(wordBeingEdited);

                // Remove old word from list
                wordList.remove(wordBeingEdited);

                // Add updated word
                userDictionary.put(badWord, replacement);
                wordList.add(badWord);

                wordBeingEdited = null;

                saveButton.setText("Save");

                Toast.makeText(
                        this,
                        "Updated: " + badWord,
                        Toast.LENGTH_SHORT
                ).show();

            } else {

                // ADDING a new word
                userDictionary.put(badWord, replacement);

                if (!wordList.contains(badWord)) {
                    wordList.add(badWord);
                }

                Toast.makeText(
                        this,
                        "Saved: " + badWord,
                        Toast.LENGTH_SHORT
                ).show();
            }

            // Save everything
            saveWordsToStorage();

            // Refresh list
            adapter.notifyDataSetChanged();

            // Clear inputs
            badWordInput.setText("");
            replacementInput.setText("");
        });
    }

    // Save dictionary to SharedPreferences
    private void saveWordsToStorage() {
        try {
            JSONObject jsonObject = new JSONObject(userDictionary);

            prefs.edit()
                    .putString("word_map", jsonObject.toString())
                    .apply();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load dictionary from SharedPreferences
    private void loadExistingWords() {
        try {

            String jsonString = prefs.getString(
                    "word_map",
                    "{}"
            );

            JSONObject jsonObject = new JSONObject(jsonString);

            Iterator<String> keys = jsonObject.keys();

            while (keys.hasNext()) {

                String key = keys.next();

                userDictionary.put(
                        key,
                        jsonObject.getString(key)
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

You also need the adapter.

2. Create WordAdapter.java

Create:

app/src/main/java/com/example/module/WordAdapter.java

package com.example.module;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Map;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {

    private ArrayList<String> wordList;
    private Map<String, String> dictionary;

    private OnWordEditListener editListener;
    private OnWordDeleteListener deleteListener;

    public interface OnWordEditListener {
        void onEdit(String word);
    }

    public interface OnWordDeleteListener {
        void onDelete(String word);
    }

    public WordAdapter(
            ArrayList<String> wordList,
            Map<String, String> dictionary,
            OnWordEditListener editListener,
            OnWordDeleteListener deleteListener
    ) {
        this.wordList = wordList;
        this.dictionary = dictionary;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word, parent, false);

        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull WordViewHolder holder,
            int position
    ) {

        String word = wordList.get(position);
        String replacement = dictionary.get(word);

        holder.wordText.setText(word);
        holder.replacementText.setText("→ " + replacement);

        holder.editButton.setOnClickListener(v ->
                editListener.onEdit(word)
        );

        holder.deleteButton.setOnClickListener(v ->
                deleteListener.onDelete(word)
        );
    }

    @Override
    public int getItemCount() {
        return wordList.size();
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {

        TextView wordText;
        TextView replacementText;

        Button editButton;
        Button deleteButton;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);

            wordText = itemView.findViewById(R.id.word_text);
            replacementText = itemView.findViewById(R.id.replacement_text);

            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
3. Create the individual list item

Create:

res/layout/item_word.xml

<?xml version="1.0" encoding="utf-8"?>

<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="12dp"
    android:gravity="center_vertical">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/word_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="badword"
            android:textSize="18sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/replacement_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="→ replacement"
            android:textSize="15sp" />

    </LinearLayout>

    <Button
        android:id="@+id/btn_edit"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Edit" />

    <Button
        android:id="@+id/btn_delete"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Delete" />

</LinearLayout>
