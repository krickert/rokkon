package com.rokkon.pipeline.chunker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;

/**
 * Provider for OpenNLP Tokenizer.
 * This class creates and provides a singleton instance of the OpenNLP Tokenizer.
 */
@ApplicationScoped
public class TokenizerProvider {

    /**
     * Produces a singleton instance of the OpenNLP SimpleTokenizer.
     * 
     * @return A Tokenizer instance
     */
    @Produces
    @Singleton
    public Tokenizer createTokenizer() {
        return SimpleTokenizer.INSTANCE;
    }
}