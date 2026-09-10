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

    private final ArrayList<String> wordList;
    private final Map<String, String> dictionary;
    private final OnWordActionListener listener;

    public interface OnWordActionListener {
        void onEdit(String badWord, String replacement);
        void onDelete(String badWord);
    }

    public WordAdapter(
            ArrayList<String> wordList,
            OnWordActionListener listener
    ) {
        this.wordList = wordList;
        this.dictionary = new java.util.HashMap<>();
        this.listener = listener;
    }

    public void updateData(
            ArrayList<String> newWordList,
            Map<String, String> newDictionary
    ) {
        wordList.clear();
        wordList.addAll(newWordList);

        dictionary.clear();
        dictionary.putAll(newDictionary);

        notifyDataSetChanged();
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
                listener.onEdit(word, replacement)
        );

        holder.deleteButton.setOnClickListener(v ->
                listener.onDelete(word)
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

        WordViewHolder(@NonNull View itemView) {
            super(itemView);

            wordText = itemView.findViewById(R.id.word_text);
            replacementText = itemView.findViewById(R.id.replacement_text);
            editButton = itemView.findViewById(R.id.btn_edit);
            deleteButton = itemView.findViewById(R.id.btn_delete);
        }
    }
}
