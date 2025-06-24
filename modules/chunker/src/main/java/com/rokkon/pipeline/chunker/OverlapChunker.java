package com.rokkon.pipeline.chunker;

import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

/**
 * Core chunking implementation that breaks text into overlapping chunks.
 * Uses token-based chunking with configurable size and overlap parameters.
 * Supports URL preservation during chunking.
 */
@Singleton
public class OverlapChunker {

    private static final Logger LOG = Logger.getLogger(OverlapChunker.class);
    private static final long MAX_TEXT_BYTES = 100 * 1024 * 1024; // 100MB limit
    private static final int MAX_CHUNKS_PER_DOCUMENT = 1000; // Limit chunks to prevent gRPC message size issues
    private final Tokenizer tokenizer;

    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b(?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            Pattern.CASE_INSENSITIVE);
    private static final String URL_PLACEHOLDER_PREFIX = "__URL_PLACEHOLDER_";
    private static final String URL_PLACEHOLDER_SUFFIX = "__";

    @Inject
    public OverlapChunker(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    /**
     * Helper method to squish a list of strings into a single string.
     * 
     * @param list List of strings to squish
     * @return A list containing a single concatenated string, or empty list if input is empty
     */
    public List<String> squish(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        StringBuilder currentString = new StringBuilder();
        for (String s : list) {
            if (s != null && !s.isEmpty()) {
                if (currentString.length() > 0) {
                    currentString.append(" ");
                }
                currentString.append(s.trim());
            }
        }
        if (currentString.length() > 0) {
            result.add(currentString.toString());
        }
        return result;
    }

    /**
     * Transforms URLs in text to placeholders to preserve them during chunking.
     * 
     * @param text Text to process
     * @param placeholderToUrlMap Map to store placeholder-to-URL mappings
     * @param urlSpans List to store original URL spans
     * @return Text with URLs replaced by placeholders
     */
    private String transformURLsToPlaceholders(String text, Map<String, String> placeholderToUrlMap, List<Span> urlSpans) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int placeholderIndex = 0;
        while (matcher.find()) {
            String placeholder = URL_PLACEHOLDER_PREFIX + placeholderIndex + URL_PLACEHOLDER_SUFFIX;
            String url = matcher.group(0);
            placeholderToUrlMap.put(placeholder, url);
            urlSpans.add(new Span(matcher.start(), matcher.end(), "URL")); // Store original URL span
            matcher.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
            placeholderIndex++;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Restores URL placeholders in a chunk back to their original URLs.
     * 
     * @param chunkText Chunk text with placeholders
     * @param placeholderToUrlMap Map of placeholder-to-URL mappings
     * @return Chunk text with original URLs restored
     */
    private String restorePlaceholdersInChunk(String chunkText, Map<String, String> placeholderToUrlMap) {
        if (chunkText == null || chunkText.isEmpty() || placeholderToUrlMap.isEmpty()) {
            return chunkText;
        }
        String restoredText = chunkText;
        for (Map.Entry<String, String> entry : placeholderToUrlMap.entrySet()) {
            restoredText = restoredText.replaceAll(Pattern.quote(entry.getKey()), Matcher.quoteReplacement(entry.getValue()));
        }
        return restoredText;
    }

    /**
     * Extracts text from a specific field in a PipeDoc.
     * 
     * @param document The PipeDoc to extract from
     * @param fieldPath Path to the field (e.g., "body", "title")
     * @return Optional containing the extracted text, or empty if not found
     */
    private Optional<String> extractTextFromPipeDoc(PipeDoc document, String fieldPath) {
        if (document == null || fieldPath == null || fieldPath.isEmpty()) {
            return Optional.empty();
        }

        try {
            // For now, only support the commonly used fields directly
            switch (fieldPath.toLowerCase()) {
                case "body":
                    return document.hasBody() ? Optional.of(document.getBody()) : Optional.empty();
                case "title":
                    return document.hasTitle() ? Optional.of(document.getTitle()) : Optional.empty();
                case "id":
                    return Optional.of(document.getId());
                default:
                    LOG.warnf("Field '%s' is not supported. Only 'body', 'title', and 'id' are currently supported.", fieldPath);
                    return Optional.empty();
            }

        } catch (Exception e) {
            LOG.errorf("Error extracting field '%s': %s", fieldPath, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Main method to create chunks from a document.
     * 
     * @param document The PipeDoc to chunk
     * @param options Chunking configuration options
     * @param streamId Stream ID for logging and chunk ID generation
     * @param pipeStepName Pipeline step name for logging
     * @return ChunkingResult containing the created chunks and URL placeholder mappings
     */
    public ChunkingResult createChunks(PipeDoc document, ChunkerOptions options, String streamId, String pipeStepName) {
        if (document == null) {
            LOG.warnf("Input document is null. Cannot create chunks. streamId: %s, pipeStepName: %s", streamId, pipeStepName);
            return new ChunkingResult(Collections.emptyList(), Collections.emptyMap()); // Return empty result
        }
        String documentId = document.getId();
        String textFieldPath = options.sourceField();

        Optional<String> textOptional = extractTextFromPipeDoc(document, textFieldPath);
        if (textOptional.isEmpty() || textOptional.get().trim().isEmpty()) {
            LOG.warnf("No text found or text is empty at path '%s'. No chunks will be created. streamId: %s, pipeStepName: %s", 
                    textFieldPath, streamId, pipeStepName);
            return new ChunkingResult(Collections.emptyList(), Collections.emptyMap()); // Return empty result
        }
        String originalText = textOptional.get();
        
        // Sanitize the text to ensure valid UTF-8 encoding before processing
        originalText = UnicodeSanitizer.sanitizeInvalidUnicode(originalText);

        // Handle MAX_TEXT_BYTES before URL processing to avoid issues with placeholder lengths
        byte[] originalTextBytes = originalText.getBytes(StandardCharsets.UTF_8);
        if (originalTextBytes.length > MAX_TEXT_BYTES) {
            LOG.warnf("Original text from field '%s' exceeds MAX_TEXT_BYTES (%d bytes). Truncating. streamId: %s, pipeStepName: %s",
                    textFieldPath, MAX_TEXT_BYTES, streamId, pipeStepName);
            originalText = new String(originalTextBytes, 0, (int) MAX_TEXT_BYTES, StandardCharsets.UTF_8);
        }

        Map<String, String> placeholderToUrlMap = new HashMap<>();
        List<Span> originalUrlSpans = new ArrayList<>(); // To store original URL positions
        String textToProcess = originalText;

        if (options.preserveUrls() != null && options.preserveUrls()) {
            textToProcess = transformURLsToPlaceholders(originalText, placeholderToUrlMap, originalUrlSpans);
            LOG.debugf("Text after URL placeholder replacement: %s", textToProcess);
        }

        Span[] tokenSpans = tokenizer.tokenizePos(textToProcess); // Get tokens with their character spans
        String[] tokens = Span.spansToStrings(tokenSpans, textToProcess);

        if (tokens.length == 0) {
            LOG.infof("No tokens found after tokenization for document part from field '%s'. streamId: %s, pipeStepName: %s", 
                    textFieldPath, streamId, pipeStepName);
            return new ChunkingResult(Collections.emptyList(), placeholderToUrlMap); // Return empty chunks but include map
        }

        LOG.infof("Creating chunks with target character size: %d, character overlap: %d, for document ID: %s, streamId: %s, pipeStepName: %s",
                options.chunkSize(), options.chunkOverlap(), documentId, streamId, pipeStepName);

        List<Chunk> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int currentTokenStartIndex = 0;

        while (currentTokenStartIndex < tokens.length) {
            int currentTokenEndIndex = currentTokenStartIndex;
            StringBuilder currentChunkTextBuilder = new StringBuilder();

            // Determine the actual start character offset of the first token in this chunk
            int chunkStartCharOffsetInProcessedText = tokenSpans[currentTokenStartIndex].getStart();

            boolean firstTokenInChunk = true; // Flag to avoid leading space

            while (currentTokenEndIndex < tokens.length) {
                String tokenText = tokens[currentTokenEndIndex];
                int tokenCharLength = tokenText.length();
                boolean isPlaceholder = tokenText.startsWith(URL_PLACEHOLDER_PREFIX) && tokenText.endsWith(URL_PLACEHOLDER_SUFFIX);

                int potentialLength = currentChunkTextBuilder.length() + (currentChunkTextBuilder.length() > 0 ? 1 : 0) + tokenCharLength;
                if (isPlaceholder && (options.preserveUrls() != null && options.preserveUrls())) {
                    String originalUrl = placeholderToUrlMap.get(tokenText);
                    if (originalUrl != null) {
                        potentialLength = currentChunkTextBuilder.length() + (currentChunkTextBuilder.length() > 0 ? 1 : 0) + originalUrl.length();
                    }
                }

                if (!firstTokenInChunk && potentialLength > options.chunkSize()) { // Check length before adding if not the first token
                    break;
                }

                if (!firstTokenInChunk) {
                    // Smart spacing: Don't add a space if the current token is punctuation
                    // that typically doesn't have a preceding space, or if the previous token
                    // was an opening bracket, etc.
                    if (!(tokenText.length() == 1 && ".?!,:;)]}".contains(tokenText))) {
                        // Also, don't add a space if the previous token ended with something like an opening quote or bracket
                        if (currentChunkTextBuilder.length() > 0) {
                            char lastCharOfPrevious = currentChunkTextBuilder.charAt(currentChunkTextBuilder.length() - 1);
                            if (!"([{".contains(String.valueOf(lastCharOfPrevious))) {
                                currentChunkTextBuilder.append(" ");
                            }
                        } else {
                            currentChunkTextBuilder.append(" "); // Should not happen if firstTokenInChunk is true
                        }
                    }
                }
                currentChunkTextBuilder.append(tokenText);
                firstTokenInChunk = false; // No longer the first token after one is appended
                currentTokenEndIndex++;
            }

            if (currentChunkTextBuilder.length() == 0 && currentTokenEndIndex < tokens.length) {
                // Handle cases where a single token might be larger than chunk_size
                // or if we are at the very end with a small remaining token.
                // For now, we'll just take the single token if the builder is empty.
                currentChunkTextBuilder.append(tokens[currentTokenStartIndex]);
                currentTokenEndIndex = currentTokenStartIndex + 1;
            }

            String chunkTextWithPlaceholders = currentChunkTextBuilder.toString().trim();
            if (chunkTextWithPlaceholders.isEmpty()) {
                if (currentTokenEndIndex >= tokens.length) break; // No more tokens to process
                currentTokenStartIndex = currentTokenEndIndex; // Skip empty formation and advance
                continue;
            }

            // Determine the actual end character offset of the last token in this chunk
            int chunkEndCharOffsetInProcessedText = tokenSpans[currentTokenEndIndex - 1].getEnd();

            String finalChunkText = chunkTextWithPlaceholders;
            if (options.preserveUrls() != null && options.preserveUrls()) {
                finalChunkText = restorePlaceholdersInChunk(chunkTextWithPlaceholders, placeholderToUrlMap);
            }

            // Calculate Original Offsets
            int originalStartOffset = chunkStartCharOffsetInProcessedText;
            int originalEndOffset = chunkEndCharOffsetInProcessedText - 1; // Span.getEnd() is exclusive

            if (options.preserveUrls() != null && options.preserveUrls() && !placeholderToUrlMap.isEmpty()) {
                // Placeholder for accurate offset recalculation logic
                // This would involve iterating through originalUrlSpans and adjusting offsets
                // based on whether the chunk's span in processedText overlaps with placeholder spans.
                // For now, we'll use the processed text offsets, which will be inaccurate if URLs were replaced.
                LOG.warnf("URL preservation is active, original character offsets for chunks might be approximate " +
                         "due to placeholder substitutions. StreamID: %s, DocID: %s", streamId, documentId);
            }

            String chunkId = String.format(options.chunkIdTemplate(), streamId, documentId, chunkIndex++);
            chunks.add(new Chunk(chunkId, finalChunkText, originalStartOffset, originalEndOffset));

            // Determine next starting token for overlap
            if (currentTokenEndIndex >= tokens.length) {
                break; // Reached the end of tokens
            }

            // Calculate overlap in terms of characters and find a suitable token to start next chunk
            int desiredOverlapChars = options.chunkOverlap();
            int currentChunkLengthChars = chunkEndCharOffsetInProcessedText - chunkStartCharOffsetInProcessedText;
            int stepBackChars = Math.max(0, currentChunkLengthChars - desiredOverlapChars);

            int nextTokenStartCharTarget = chunkStartCharOffsetInProcessedText + stepBackChars;

            int nextTokenCandidate = currentTokenStartIndex; // Start searching from the beginning of the current chunk
            for (int i = currentTokenStartIndex; i < currentTokenEndIndex; i++) {
                if (tokenSpans[i].getStart() >= nextTokenStartCharTarget) {
                    nextTokenCandidate = i;
                    break;
                }
                // If no token starts exactly at/after target, the last token before it is the best bet
                // or simply the one that makes the overlap closest to desired.
                nextTokenCandidate = i;
            }
            // Ensure progress: if the overlap is too large or chunks too small,
            // we must advance at least one token.
            currentTokenStartIndex = Math.max(currentTokenStartIndex + 1, nextTokenCandidate);
        }

        LOG.infof("Created %d token-based chunks for document part from field '%s'. streamId: %s, pipeStepName: %s",
                chunks.size(), textFieldPath, streamId, pipeStepName);
        // Return the chunks and the map
        return new ChunkingResult(chunks, placeholderToUrlMap);
    }
}