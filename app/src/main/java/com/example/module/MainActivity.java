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

    private Map<String, String> userDictionary = new HashMap<>();
    private SharedPreferences prefs;
    private ArrayList<String> wordList;
    private WordAdapter adapter;
    private String wordBeingEdited = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("mod_prefs", Context.MODE_PRIVATE);
        loadExistingWords();

        EditText badWordInput = findViewById(R.id.input_bad_word);
        EditText replacementInput = findViewById(R.id.input_replacement);
        Button saveButton = findViewById(R.id.btn_save);
        RecyclerView recyclerView = findViewById(R.id.recycler_words);

        wordList = new ArrayList<>(userDictionary.keySet());

        adapter = new WordAdapter(
                wordList,
                userDictionary,
                word -> {
                    badWordInput.setText(word);
                    replacementInput.setText(userDictionary.get(word));
                    wordBeingEdited = word;
                    saveButton.setText("Update");
                },
                word -> {
                    userDictionary.remove(word);
                    wordList.remove(word);
                    saveWordsToStorage();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Deleted: " + word, Toast.LENGTH_SHORT).show();
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        saveButton.setOnClickListener(v -> {
            String badWord = badWordInput.getText().toString().trim().toLowerCase();
            String replacement = replacementInput.getText().toString().trim();

            if (badWord.isEmpty()) {
                Toast.makeText(this, "Enter a word", Toast.LENGTH_SHORT).show();
                return;
            }

            if (wordBeingEdited != null) {
                userDictionary.remove(wordBeingEdited);
                wordList.remove(wordBeingEdited);
                userDictionary.put(badWord, replacement);
                wordList.add(badWord);
                wordBeingEdited = null;
                saveButton.setText("Save");
                Toast.makeText(this, "Updated: " + badWord, Toast.LENGTH_SHORT).show();
            } else {
                userDictionary.put(badWord, replacement);
                if (!wordList.contains(badWord)) {
                    wordList.add(badWord);
                }
                Toast.makeText(this, "Saved: " + badWord, Toast.LENGTH_SHORT).show();
            }

            saveWordsToStorage();
            adapter.notifyDataSetChanged();
            badWordInput.setText("");
            replacementInput.setText("");
        });
    }

    private void saveWordsToStorage() {
        try {
            JSONObject jsonObject = new JSONObject(userDictionary);
            prefs.edit().putString("word_map", jsonObject.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadExistingWords() {
        try {
            String jsonString = prefs.getString("word_map", "{}");
            JSONObject jsonObject = new JSONObject(jsonString);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                userDictionary.put(key, jsonObject.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
