package com.rokkon.pipeline.chunker;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for sanitizing Unicode text to ensure valid UTF-8 encoding.
 * This helps prevent issues with surrogate pairs and invalid character sequences
 * when text is serialized through gRPC/Protobuf.
 */
public class UnicodeSanitizer {

    /**
     * Sanitizes a string by converting it to UTF-8 bytes and back, replacing any
     * malformed or unmappable characters with the Unicode replacement character.
     * 
     * @param text The text to sanitize
     * @return A sanitized version of the text with invalid sequences replaced
     */
    public static String sanitizeInvalidUnicode(String text) {
        if (text == null) {
            return null;
        }
        
        // Convert String to bytes using UTF-8 (this assumes the String is already valid UTF-16)
        byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);

        // Create a decoder that replaces malformed input and unmappable characters
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPLACE)
                                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        try {
            // Decode the bytes back to a String, replacing invalid sequences
            CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(utf8Bytes));
            return charBuffer.toString();
        } catch (CharacterCodingException e) {
            // This shouldn't happen with REPLACE actions, but good practice
            System.err.println("Unexpected character coding exception during sanitization: " + e.getMessage());
            return text; // Fallback to original
        }
    }
    
    /**
     * Checks if a string contains valid UTF-8 when encoded.
     * 
     * @param text The text to check
     * @return true if the text can be encoded to UTF-8 without errors
     */
    public static boolean isValidUtf8(String text) {
        if (text == null) {
            return true;
        }
        
        try {
            // Try to encode to UTF-8 with REPORT mode (throws on invalid)
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            
            byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
            decoder.decode(ByteBuffer.wrap(utf8Bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}