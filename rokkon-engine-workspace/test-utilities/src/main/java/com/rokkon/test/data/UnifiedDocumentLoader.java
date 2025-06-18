package com.rokkon.test.data;

import com.google.protobuf.ExtensionRegistryLite;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class UnifiedDocumentLoader {

    // It's good practice to pre-build an ExtensionRegistry if you use Protobuf extensions,
    // though for simple messages it might not be strictly necessary.
    // For this example, we'll just use the default parseFrom which doesn't require it.
    // If you do have extensions, you'd register them here.
    private static final ExtensionRegistryLite EXTENSION_REGISTRY = ExtensionRegistryLite.newInstance();

    public static Collection<PipeDoc> loadPipeDocsFromDirectory(String directory, String fileExtension) {
        return loadProtobufMessages(directory, fileExtension, PipeDoc.parser());
    }

    public static Collection<PipeStream> loadPipeStreamsFromDirectory(String directory, String fileExtension) {
        return loadProtobufMessages(directory, fileExtension, PipeStream.parser());
    }

    /**
     * Generic method to load Protobuf messages from a directory.
     *
     * @param directory The path to the directory containing the Protobuf binary files.
     * @param fileExtension The file extension of the Protobuf binary files (e.g., "bin", "pb").
     * @param parser The Protobuf parser for the specific message type (e.g., PipeDoc.parser()).
     * @param <T> The type of the Protobuf message.
     * @return A collection of parsed Protobuf messages.
     */
    private static <T extends com.google.protobuf.MessageLite> Collection<T> loadProtobufMessages(
            String directory, String fileExtension, com.google.protobuf.Parser<T> parser) {
        Path dirPath = Paths.get(directory);
        if (!Files.isDirectory(dirPath)) {
            System.err.println("Error: Directory not found or not a directory: " + directory);
            return Collections.emptyList();
        }

        List<T> messages = Collections.emptyList();
        try (Stream<Path> paths = Files.walk(dirPath)) {
            messages = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("." + fileExtension))
                    .map(filePath -> {
                        try {
                            // Read all bytes from the file
                            byte[] data = Files.readAllBytes(filePath);
                            // Parse the bytes using the provided Protobuf parser
                            // This does NOT use a custom ClassLoader for parsing the bytes,
                            // but relies on the Protobuf generated classes being available
                            // in the current thread's context classloader or system classloader.
                            return parser.parseFrom(data, EXTENSION_REGISTRY);
                        } catch (IOException e) {
                            System.err.println("Error reading file " + filePath + ": " + e.getMessage());
                            return null; // Or throw a custom exception
                        }
                    })
                    .filter(java.util.Objects::nonNull) // Filter out any nulls from parsing errors
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("Error walking directory " + directory + ": " + e.getMessage());
        }
        return messages;
    }

}