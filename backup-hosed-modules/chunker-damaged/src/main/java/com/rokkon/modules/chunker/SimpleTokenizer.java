package com.rokkon.modules.chunker;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

/**
 * Simple tokenizer wrapper that uses WhitespaceTokenizer when OpenNLP models are not available.
 */
public class SimpleTokenizer implements Tokenizer {
    
    private final Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;

    @Override
    public String[] tokenize(String s) {
        return tokenizer.tokenize(s);
    }
    
    @Override
    public Span[] tokenizePos(String s) {
        return WhitespaceTokenizer.INSTANCE.tokenizePos(s);
    }
}