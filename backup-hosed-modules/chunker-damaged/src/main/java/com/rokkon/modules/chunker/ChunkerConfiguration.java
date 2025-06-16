package com.rokkon.modules.chunker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Configuration class for OpenNLP models used by the chunker module.
 * Provides CDI producers for tokenizer and sentence detector models.
 */
@ApplicationScoped
public class ChunkerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChunkerConfiguration.class);

    @Produces
    @Singleton
    public Tokenizer createTokenizer() {
        try {
            log.info("Loading OpenNLP tokenizer model");
            InputStream modelStream = getClass().getResourceAsStream("/opennlp/en-token.bin");
            if (modelStream == null) {
                log.warn("OpenNLP tokenizer model not found, using WhitespaceTokenizer");
                return new SimpleTokenizer();
            }
            
            TokenizerModel model = new TokenizerModel(modelStream);
            modelStream.close();
            log.info("Successfully loaded OpenNLP tokenizer model");
            return new TokenizerME(model);
            
        } catch (IOException e) {
            log.error("Failed to load OpenNLP tokenizer model: {}", e.getMessage());
            log.info("Using WhitespaceTokenizer as fallback");
            return new SimpleTokenizer();
        }
    }

    @Produces
    @Singleton
    public SentenceDetector createSentenceDetector() {
        try {
            log.info("Loading OpenNLP sentence detector model");
            InputStream modelStream = getClass().getResourceAsStream("/opennlp/en-sent.bin");
            if (modelStream == null) {
                log.warn("OpenNLP sentence detector model not found, using simple sentence detector");
                return new SimpleSentenceDetector();
            }
            
            SentenceModel model = new SentenceModel(modelStream);
            modelStream.close();
            log.info("Successfully loaded OpenNLP sentence detector model");
            return new SentenceDetectorME(model);
            
        } catch (IOException e) {
            log.error("Failed to load OpenNLP sentence detector model: {}", e.getMessage());
            log.info("Using simple sentence detector as fallback");
            return new SimpleSentenceDetector();
        }
    }


}