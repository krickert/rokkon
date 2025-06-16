package com.rokkon.modules.tika.util;

import com.rokkon.search.model.PipeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Utility class for loading test data from protobuf binary files.
 * Used to load the 99 test documents for comprehensive testing.
 */
public class TestDataLoader {
    private static final Logger LOG = LoggerFactory.getLogger(TestDataLoader.class);
    
    /**
     * Loads all PipeDoc test documents from the tika-pipe-docs-large directory.
     * These are the 99 documents used to verify quality and compatibility.
     * 
     * @return List of PipeDoc objects loaded from binary files
     */
    public static List<PipeDoc> loadAllTikaTestDocuments() {
        List<PipeDoc> documents = new ArrayList<>();
        
        // Known filename patterns from the actual test data
        String[] knownHashes = {
            "e15df1cf", "604f705a", "8f552dc2", "de78fd0c", "99f324f5",
            "a0a63e33", "31f0a42a", "bb188875", "8b13b505", "0fc98b19",
            "7936fc13", "c60e5b5a", "1ff58ae8", "da66ea82", "be158727",
            "db4984fc", "76f06bc7", "0c2b4c87", "b1821265", "5086b36d",
            "a3b7cc0a", "031f1056", "c241349c", "18bffb5f", "bd68dfd7",
            "15cd17e7", "abd99abc", "8650644d", "a9119b2f", "d3fb59f9",
            "35e9843d", "db9f9ec4", "3b0cfc43", "37e53d8b", "686a4d76",
            "20cc54d2", "6fa36f13", "ef7dcb28", "f8c29085", "4f190926",
            "5aca648b", "a8386257", "d89ecf60", "d1eaaa97", "dd02ea9e",
            "872f8f5a", "5ce4be16", "7321d9bb", "7e47daa0", "a4f5640a",
            "b8626f2c", "0c2db9eb", "20313086", "3be4f3bb", "8198d3af",
            "b54ab2d4", "79f942a7", "af68bbe9", "94dfa801", "e7f91de4",
            "9d5b3cba", "d518abb6", "24574444", "f82360c2", "f0fb1715",
            "7550c76c", "5a52fec3", "818653e8", "ff59a243", "1ecb7d4f",
            "9311958b", "a5d419c3", "6ef6bd95", "aa07441b", "1e97a609",
            "bf388c2a", "6f0b8635", "75c66f9e", "792a7785", "9c48ab2d",
            "de6d1878", "0e04c944", "c1aed3e9", "0f65cf07", "a081ee3a",
            "22d65251", "3b428498", "8fa04bf5", "10cf5d93", "dcb3f764",
            "d3e7d2ce", "0793b7f8", "f6d8d283", "a577959c", "3f12b3b6",
            "fc958c79", "125a07d5", "8171351b", "4f5ff2d7", "d622eaca"
        };
        
        ClassLoader classLoader = TestDataLoader.class.getClassLoader();
        String basePath = "test-data/tika-pipe-docs-large";
        
        // Load files using the known hash patterns
        for (int i = 0; i < knownHashes.length; i++) {
            String filename = String.format("tika_doc_%03d_doc-%03d-%s.bin", i, i, knownHashes[i]);
            String fullPath = basePath + "/" + filename;
            
            try (InputStream is = classLoader.getResourceAsStream(fullPath)) {
                if (is != null) {
                    PipeDoc doc = PipeDoc.parseFrom(is);
                    documents.add(doc);
                    LOG.debug("Loaded document {}: {} (size: {})", i, doc.getId(), 
                        doc.hasBlob() ? doc.getBlob().getData().size() : 0);
                } else {
                    LOG.warn("Could not find file: {}", fullPath);
                }
            } catch (IOException e) {
                LOG.error("Error loading document from {}: {}", filename, e.getMessage());
            }
        }
        
        LOG.info("Loaded {} test documents from {}", documents.size(), basePath);
        return documents;
    }
    
    /**
     * Simple method to count available test documents without loading them all.
     */
    public static int countAvailableTestDocuments() {
        return loadAllTikaTestDocuments().size();
    }
}