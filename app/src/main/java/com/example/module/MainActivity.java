package com.example.module;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivity extends Activity {
    
    // This is our list of words
    private Map<String, String> userDictionary = new HashMap<>();
    
    // This is the tool that saves data to the phone
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Get the shared storage file named "mod_prefs"
        prefs = getSharedPreferences("mod_prefs", Context.MODE_PRIVATE);
        
        // Load any words we already saved in the past
        loadExistingWords();

        // 2. Find our text boxes and button from the XML layout
        EditText badWordInput = findViewById(R.id.input_bad_word);
        EditText replacementInput = findViewById(R.id.input_replacement);
        Button saveButton = findViewById(R.id.btn_save);

        // 3. What happens when you click Save?
        saveButton.setOnClickListener(v -> {
            String badWord = badWordInput.getText().toString().trim().toLowerCase();
            String replacement = replacementInput.getText().toString().trim();

            if (!badWord.isEmpty()) {
                // Add the new word to our list
                userDictionary.put(badWord, replacement);
                saveWordsToStorage();
                
                // Show a little popup message saying it worked
                Toast.makeText(this, "Saved: " + badWord, Toast.LENGTH_SHORT).show();
                
                // Clear the text boxes for the next word
                badWordInput.setText("");
                replacementInput.setText("");
            }
        });
    }

    private void saveWordsToStorage() {
        try {
            // Convert our list into a JSON string and save it
            JSONObject jsonObject = new JSONObject(userDictionary);
            prefs.edit().putString("word_map", jsonObject.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadExistingWords() {
        try {
            // Read the saved string, or use empty brackets {} if nothing is saved yet
            String jsonString = prefs.getString("word_map", "{}");
            JSONObject jsonObject = new JSONObject(jsonString);
            
            // Put the words back into our dictionary list
            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                userDictionary.put(key, jsonObject.getString(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
