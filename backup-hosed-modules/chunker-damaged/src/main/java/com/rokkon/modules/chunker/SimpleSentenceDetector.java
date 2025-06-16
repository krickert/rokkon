package com.rokkon.modules.chunker;

import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;

/**
 * Simple sentence detector that doesn't extend SentenceDetectorME to avoid NPE issues.
 * Provides basic sentence detection based on punctuation.
 */
public class SimpleSentenceDetector implements SentenceDetector {
    
    @Override
    public String[] sentDetect(CharSequence s) {
        if (s == null || s.toString().trim().isEmpty()) {
            return new String[0];
        }
        return s.toString().split("(?<=[.!?])\\s+");
    }
    
    @Override
    public Span[] sentPosDetect(CharSequence s) {
        String text = s.toString();
        String[] sentences = sentDetect(s);
        Span[] spans = new Span[sentences.length];
        
        int currentPos = 0;
        for (int i = 0; i < sentences.length; i++) {
            int sentStart = text.indexOf(sentences[i], currentPos);
            if (sentStart == -1) sentStart = currentPos;
            int sentEnd = sentStart + sentences[i].length();
            spans[i] = new Span(sentStart, sentEnd);
            currentPos = sentEnd;
        }
        
        return spans;
    }
}