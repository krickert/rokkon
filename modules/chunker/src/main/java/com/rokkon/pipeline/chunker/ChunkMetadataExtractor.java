package com.rokkon.pipeline.chunker;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Extracts metadata from text chunks to provide additional context and information.
 * This includes statistics like word count, sentence count, and various text characteristics.
 */
@Singleton
public class ChunkMetadataExtractor {

    private static final Logger LOG = Logger.getLogger(ChunkMetadataExtractor.class);
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*([*\\-+•]|[0-9]+[.)])\\s+.*");

    private final SentenceDetector sentenceDetector;
    private final Tokenizer tokenizer;

    @Inject
    public ChunkMetadataExtractor(SentenceDetector sentenceDetector, Tokenizer tokenizer) {
        this.sentenceDetector = sentenceDetector;
        this.tokenizer = tokenizer;
    }

    /**
     * Extracts comprehensive metadata from a text chunk.
     *
     * @param chunkText The text content of the chunk
     * @param chunkNumber The position of this chunk in the sequence (0-based)
     * @param totalChunksInDocument Total number of chunks in the document
     * @param containsUrlPlaceholder Whether the chunk contains URL placeholders
     * @return A map of metadata key-value pairs
     */
    public Map<String, Value> extractAllMetadata(String chunkText, int chunkNumber, int totalChunksInDocument, boolean containsUrlPlaceholder) {
        Map<String, Value> metadataMap = new HashMap<>();

        if (StringUtils.isBlank(chunkText)) {
            metadataMap.put("word_count", Value.newBuilder().setNumberValue(0).build());
            metadataMap.put("character_count", Value.newBuilder().setNumberValue(0).build());
            metadataMap.put("sentence_count", Value.newBuilder().setNumberValue(0).build());
            return metadataMap;
        }

        int characterCount = chunkText.length();
        metadataMap.put("character_count", Value.newBuilder().setNumberValue(characterCount).build());

        String[] sentences = sentenceDetector.sentDetect(chunkText);
        int sentenceCount = sentences.length;
        metadataMap.put("sentence_count", Value.newBuilder().setNumberValue(sentenceCount).build());

        String[] tokens = tokenizer.tokenize(chunkText);
        int wordCount = tokens.length;
        metadataMap.put("word_count", Value.newBuilder().setNumberValue(wordCount).build());

        double avgWordLength = wordCount > 0 ? (double) Arrays.stream(tokens).mapToInt(String::length).sum() / wordCount : 0;
        metadataMap.put("average_word_length", Value.newBuilder().setNumberValue(Double.parseDouble(DECIMAL_FORMAT.format(avgWordLength))).build());

        double avgSentenceLength = sentenceCount > 0 ? (double) wordCount / sentenceCount : 0;
        metadataMap.put("average_sentence_length", Value.newBuilder().setNumberValue(Double.parseDouble(DECIMAL_FORMAT.format(avgSentenceLength))).build());

        if (wordCount > 0) {
            Set<String> uniqueTokens = new HashSet<>(Arrays.asList(tokens));
            double ttr = (double) uniqueTokens.size() / wordCount;
            metadataMap.put("vocabulary_density", Value.newBuilder().setNumberValue(Double.parseDouble(DECIMAL_FORMAT.format(ttr))).build());
        } else {
            metadataMap.put("vocabulary_density", Value.newBuilder().setNumberValue(0).build());
        }

        long whitespaceChars = chunkText.chars().filter(Character::isWhitespace).count();
        long alphanumericChars = chunkText.chars().filter(Character::isLetterOrDigit).count();
        long digitChars = chunkText.chars().filter(Character::isDigit).count();
        long uppercaseChars = chunkText.chars().filter(Character::isUpperCase).count();

        metadataMap.put("whitespace_percentage", Value.newBuilder().setNumberValue(characterCount > 0 ? Double.parseDouble(DECIMAL_FORMAT.format((double) whitespaceChars / characterCount)) : 0).build());
        metadataMap.put("alphanumeric_percentage", Value.newBuilder().setNumberValue(characterCount > 0 ? Double.parseDouble(DECIMAL_FORMAT.format((double) alphanumericChars / characterCount)) : 0).build());
        metadataMap.put("digit_percentage", Value.newBuilder().setNumberValue(characterCount > 0 ? Double.parseDouble(DECIMAL_FORMAT.format((double) digitChars / characterCount)) : 0).build());
        metadataMap.put("uppercase_percentage", Value.newBuilder().setNumberValue(characterCount > 0 ? Double.parseDouble(DECIMAL_FORMAT.format((double) uppercaseChars / characterCount)) : 0).build());

        Struct.Builder punctuationStruct = Struct.newBuilder();
        Map<Character, Integer> puncCounts = new HashMap<>();
        for (char c : chunkText.toCharArray()) {
            if (StringUtils.isAsciiPrintable(String.valueOf(c)) && !Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                puncCounts.put(c, puncCounts.getOrDefault(c, 0) + 1);
            }
        }
        for (Map.Entry<Character, Integer> entry : puncCounts.entrySet()) {
            punctuationStruct.putFields(String.valueOf(entry.getKey()), Value.newBuilder().setNumberValue(entry.getValue()).build());
        }
        metadataMap.put("punctuation_counts", Value.newBuilder().setStructValue(punctuationStruct).build());

        metadataMap.put("is_first_chunk", Value.newBuilder().setBoolValue(chunkNumber == 0).build());
        metadataMap.put("is_last_chunk", Value.newBuilder().setBoolValue(chunkNumber == totalChunksInDocument - 1).build());
        if (totalChunksInDocument > 0) {
            double relativePosition = (totalChunksInDocument == 1) ? 0.0 : (double) chunkNumber / (totalChunksInDocument - 1);
            metadataMap.put("relative_position", Value.newBuilder().setNumberValue(Double.parseDouble(DECIMAL_FORMAT.format(relativePosition))).build());
        } else {
            metadataMap.put("relative_position", Value.newBuilder().setNumberValue(0).build());
        }

        metadataMap.put("contains_urlplaceholder", Value.newBuilder().setBoolValue(containsUrlPlaceholder).build());
        metadataMap.put("list_item_indicator", Value.newBuilder().setBoolValue(LIST_ITEM_PATTERN.matcher(chunkText).matches()).build());
        metadataMap.put("potential_heading_score", Value.newBuilder().setNumberValue(calculatePotentialHeadingScore(chunkText, tokens, sentenceCount)).build());

        return metadataMap;
    }

    /**
     * Calculates a score indicating how likely the text is to be a heading.
     * Higher scores (closer to 1.0) indicate greater likelihood of being a heading.
     *
     * @param chunkText The text content of the chunk
     * @param tokens The tokens in the chunk
     * @param sentenceCount The number of sentences in the chunk
     * @return A score between 0.0 and 1.0
     */
    private double calculatePotentialHeadingScore(String chunkText, String[] tokens, int sentenceCount) {
        double score = 0.0;
        if (tokens.length == 0) return 0.0;

        if (tokens.length < 10) score += 0.2;
        if (tokens.length < 5) score += 0.2;
        if (sentenceCount == 1) score += 0.3;

        if (!chunkText.isEmpty()) {
            char lastChar = chunkText.charAt(chunkText.length() - 1);
            if (Character.isLetterOrDigit(lastChar)) {
                score += 0.2;
            }
        }

        long uppercaseWords = Arrays.stream(tokens)
                .filter(token -> token.length() > 0 && Character.isUpperCase(token.charAt(0)))
                .count();
        if (tokens.length > 0 && (double) uppercaseWords / tokens.length > 0.7) {
            score += 0.2;
        }
        if (StringUtils.isAllUpperCase(chunkText.replaceAll("\\s+", ""))) {
            score += 0.2;
        }
        return Math.min(1.0, Double.parseDouble(DECIMAL_FORMAT.format(score)));
    }
}