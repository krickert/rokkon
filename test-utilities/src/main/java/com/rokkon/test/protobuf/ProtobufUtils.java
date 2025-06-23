package com.rokkon.test.protobuf;

import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.rokkon.search.model.PipeDoc;
import com.rokkon.search.model.PipeStream;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import io.quarkus.runtime.util.ClassPathUtils;

/**
 * Utility class for working with protobuf messages in the Rokkon testing framework.
 * Provides methods for saving, loading, and converting protobuf messages.
 */
@Singleton
public class ProtobufUtils {

    /**
     * Returns the current timestamp as a Timestamp object.
     *
     * @return the current timestamp
     */
    public Timestamp now() {
        Instant time = Instant.now();
        return Timestamp.newBuilder().setSeconds(time.getEpochSecond())
                .setNanos(time.getNano()).build();
    }

    /**
     * Creates a Timestamp object from the given epoch seconds.
     *
     * @param epochSeconds the number of seconds since January 1, 1970
     * @return a Timestamp object representing the given epoch seconds
     */
    public Timestamp stamp(long epochSeconds) {
        return Timestamp.newBuilder().setSeconds(epochSeconds)
                .setNanos(0).build();
    }

    /**
     * Saves a Protobuf message to disk.
     *
     * @param dst  The destination file path.
     * @param item The Protobuf message to be saved.
     * @throws IOException If an I/O error occurs while writing to the file.
     */
    public <T extends Message> void saveProtobufToDisk(String dst, T item) throws IOException {
        Path path = Paths.get(dst);
        // Ensure parent directories exist
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (OutputStream os = Files.newOutputStream(path)) {
            item.writeTo(os);
        }
    }

    /**
     * Saves a collection of Protocol Buffer messages to disk.
     *
     * @param dstPrefix The prefix of the destination file path.
     * @param items     The collection of Protocol Buffer messages to be saved.
     * @param leftPad   The number of digits used for left padding the index of each saved message in the file name.
     * @param <T>       The type of Protocol Buffer message.
     * @throws RuntimeException If an I/O error occurs while saving the messages.
     */
    public <T extends Message> void saveProtobufsToDisk(String dstPrefix, Collection<T> items, int leftPad) {
        AtomicInteger i = new AtomicInteger();
        items.forEach((item) -> {
            try {
                saveProtobufToDisk(dstPrefix + StringUtils.leftPad(String.valueOf(i.getAndIncrement()), leftPad, "0") + ".bin", item);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Saves a collection of Protocol Buffer messages to disk.
     *
     * @param dstPrefix The prefix of the destination file path.
     * @param items     The collection of Protocol Buffer messages to be saved.
     * @param <T>       The type of Protocol Buffer message.
     * @throws RuntimeException If an I/O error occurs while saving the messages.
     */
    public <T extends Message> void saveProtobufsToDisk(String dstPrefix, Collection<T> items) {
        int leftPad = (String.valueOf(items.size())).length();
        saveProtobufsToDisk(dstPrefix, items, leftPad);
    }


    /**
     * Creates a UUID key from a given string identifier.
     *
     * @param id The string identifier.
     * @return The UUID key.
     */
    public UUID createKey(String id) {
        return UUID.nameUUIDFromBytes(id.getBytes());
    }

    /**
     * Creates a UUID key from a given PipeDocument object.
     *
     * @param pipeDocument The PipeDocument object to generate the key from.
     * @return The generated UUID key.
     */
    public UUID createKey(PipeDoc pipeDocument) {
        return createKey(pipeDocument.getId());
    }

    /**
     * Creates a UUID key from a given PipeStream object.
     *
     * @param pipeStream The PipeStream object to generate the key from.
     * @return The generated UUID key.
     */
    public UUID createKey(PipeStream pipeStream) {
        return createKey(pipeStream.getStreamId());
    }

    /**
     * Creates a ListValue object from a collection of strings.
     *
     * @param collectionToConvert The collection of strings to be converted.
     * @return A ListValue object containing the converted strings.
     */
    public ListValue createListValueFromCollection(Collection<String> collectionToConvert) {
        ListValue.Builder builder = ListValue.newBuilder();
        collectionToConvert.forEach((obj) -> builder.addValues(Value.newBuilder().setStringValue(obj).build()));
        return builder.build();
    }


}