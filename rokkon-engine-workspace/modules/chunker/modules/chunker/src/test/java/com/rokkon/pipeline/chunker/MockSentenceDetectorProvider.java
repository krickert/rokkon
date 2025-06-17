package com.rokkon.pipeline.chunker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.util.Span;
import org.jboss.logging.Logger;

/**
 * Mock provider for OpenNLP SentenceDetector used in tests.
 * This provides a simple implementation that splits sentences on periods.
 */
@ApplicationScoped
@Alternative
public class MockSentenceDetectorProvider {

    private static final Logger LOG = Logger.getLogger(MockSentenceDetectorProvider.class);

    /**
     * Produces a singleton instance of a mock SentenceDetector for testing.
     * 
     * @return A simple SentenceDetector implementation
     */
    @Produces
    @Singleton
    public SentenceDetector createSentenceDetector() {
        LOG.info("Creating mock sentence detector for testing");
        return new SentenceDetector() {
            @Override
            public String[] sentDetect(CharSequence text) {
                String textStr = text.toString();
                return textStr.split("(?<=\\.)\\s+");
            }

            @Override
            public Span[] sentPosDetect(CharSequence text) {
                String textStr = text.toString();
                String[] sentences = sentDetect(textStr);
                Span[] spans = new Span[sentences.length];
                int start = 0;
                for (int i = 0; i < sentences.length; i++) {
                    spans[i] = new Span(start, start + sentences[i].length());
                    start += sentences[i].length() + 1; // +1 for the space
                }
                return spans;
            }
        };
    }
}