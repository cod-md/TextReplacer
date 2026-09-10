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
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_word, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        String word = wordList.get(position);
        String replacement = dictionary.get(word);

        holder.wordText.setText(word);
        holder.replacementText.setText("→ " + replacement);

        holder.editButton.setOnClickListener(v -> editListener.onEdit(word));
        holder.deleteButton.setOnClickListener(v -> deleteListener.onDelete(word));
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
