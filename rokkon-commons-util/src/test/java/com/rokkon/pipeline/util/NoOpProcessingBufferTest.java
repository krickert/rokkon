package com.rokkon.pipeline.util;

import com.rokkon.search.model.PipeDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors; // Added for convenience, though not strictly needed for the fix

import static org.assertj.core.api.Assertions.assertThat;

public class NoOpProcessingBufferTest {

    @TempDir
    Path tempDir; // Using TempDir to ensure clean slate for each test

    private FileSystem fileSystem;
    private Path mockSaveLocation; // Represents the default save location for some constructors

    @BeforeEach
    void setup() throws IOException {
        // Create a new in-memory file system for each test
        fileSystem = com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder.newEmpty().build();
        // Define a "default" save location within this mock file system
        mockSaveLocation = fileSystem.getPath("/test_output");
        // Ensure this directory exists in the mock FS before tests, mimicking default behavior
        Files.createDirectories(mockSaveLocation);
    }

    private PipeDoc createTestPipeDoc(String id, String title) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .build();
    }

    @Test
    void add_doesNothing() {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        assertThat(buffer.size()).isEqualTo(0);
    }

    @Test
    void saveToDisk_doesNothing_withDefaultParams() throws Exception {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        buffer.saveToDisk();

        // The NoOp buffer should not create any files.
        // We're checking the *contents* of the root of the mock file system
        // for any regular files that would have been written.
        assertThat(Files.list(fileSystem.getPath("./")).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
        // Also check if the default location we explicitly created for other tests is still empty of files.
        assertThat(Files.list(mockSaveLocation).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void saveToDisk_doesNothing_withPrefixAndPrecision() throws Exception {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        buffer.saveToDisk("custom_prefix", 5);

        assertThat(Files.list(fileSystem.getPath("./")).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
        assertThat(Files.list(mockSaveLocation).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void saveToDisk_doesNothing_withLocationPrefixAndPrecision() throws Exception {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        Path specificMockLocation = fileSystem.getPath("/specific_output");
        Files.createDirectories(specificMockLocation); // Create the target directory in mock FS

        buffer.saveToDisk(specificMockLocation, "specific_prefix", 4);

        assertThat(Files.list(specificMockLocation).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
        assertThat(Files.list(fileSystem.getPath("./")).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
        assertThat(Files.list(mockSaveLocation).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
    }

    @Test
    void size_alwaysReturnsZero() {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        assertThat(buffer.size()).isEqualTo(0);
    }

    @Test
    void clear_doesNothing() {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        buffer.clear();
        assertThat(buffer.size()).isEqualTo(0);
    }

    @Test
    void snapshot_alwaysReturnsEmptyList() {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        List<PipeDoc> snapshot = buffer.snapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot).isEmpty();
        // Specifically assert that it's the singleton empty list, as implemented in NoOpProcessingBuffer
        assertThat(snapshot).isSameAs(Collections.emptyList());
    }

    @Test
    void saveOnShutdown_doesNothing() throws IOException {
        NoOpProcessingBuffer<PipeDoc> buffer = new NoOpProcessingBuffer<>();
        buffer.add(createTestPipeDoc("test", "title"));
        buffer.saveOnShutdown();

        assertThat(Files.list(fileSystem.getPath("./")).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
        assertThat(Files.list(mockSaveLocation).filter(Files::isRegularFile).collect(Collectors.toList())).isEmpty();
    }
}