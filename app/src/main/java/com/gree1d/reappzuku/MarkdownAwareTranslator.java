package com.gree1d.reappzuku;

import android.util.Log;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownAwareTranslator {

    private static final String TAG = "MarkdownAwareTranslator";

    public interface Callback {
        void onSuccess(String translatedMarkdown);
        void onFailure(Exception e);
    }

    public interface ModelReadyCallback {
        void onReady(Translator translator);
        void onFailure(Exception e);
    }

    private static final Pattern PROTECTED_BLOCK = Pattern.compile(
        "(?s)```.*?```" +    
        "|`[^`]+`" +               
        "|^#{1,6} " +              
        "|\\[([^\\]]+)\\]\\([^)]+\\)" + 
        "|^[-*+] " +               
        "|^\\d+\\. " +             
        "|^> " +                   
        "|^---+$" +                
        "|\\*\\*|\\*|__|_|~~"      
        , Pattern.MULTILINE
    );

    public static Translator buildTranslator(String targetLangTag) {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(targetLangTag)
                .build();
        return Translation.getClient(options);
    }

    public static void downloadModelIfNeeded(Translator translator, ModelReadyCallback callback) {
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> callback.onReady(translator))
                .addOnFailureListener(callback::onFailure);
    }

    public static void translate(Translator translator, String markdown, Callback callback) {
        List<Block> blocks = split(markdown);
        translateBlocks(translator, blocks, 0, result -> {
            callback.onSuccess(reassemble(result));
        }, callback::onFailure);
    }

    private static void translateBlocks(
            Translator translator,
            List<Block> blocks,
            int index,
            java.util.function.Consumer<List<Block>> onDone,
            java.util.function.Consumer<Exception> onError) {

        if (index >= blocks.size()) {
            onDone.accept(blocks);
            return;
        }

        Block block = blocks.get(index);
        if (!block.translatable) {
            translateBlocks(translator, blocks, index + 1, onDone, onError);
            return;
        }

        translator.translate(block.content)
                .addOnSuccessListener(translated -> {
                    block.content = translated;
                    translateBlocks(translator, blocks, index + 1, onDone, onError);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Block translation failed, keeping original: " + e.getMessage());
                    translateBlocks(translator, blocks, index + 1, onDone, onError);
                });
    }

    private static List<Block> split(String markdown) {
        List<Block> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        StringBuilder currentText = new StringBuilder();

        for (String line : lines) {
            if (isProtectedLine(line)) {
                if (currentText.length() > 0) {
                    blocks.add(new Block(currentText.toString(), true));
                    currentText.setLength(0);
                }
                blocks.add(new Block(line, false));
            } else {
                if (currentText.length() > 0) currentText.append("\n");
                currentText.append(line);
            }
        }

        if (currentText.length() > 0) {
            blocks.add(new Block(currentText.toString(), true));
        }

        return blocks;
    }

    private static boolean isProtectedLine(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("```")
                || trimmed.startsWith("#")
                || trimmed.startsWith("---")
                || trimmed.startsWith("| ")
                || trimmed.isEmpty();
    }

    private static String reassemble(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(blocks.get(i).content);
        }
        return sb.toString();
    }

    private static class Block {
        String content;
        final boolean translatable;

        Block(String content, boolean translatable) {
            this.content = content;
            this.translatable = translatable;
        }
    }
}
