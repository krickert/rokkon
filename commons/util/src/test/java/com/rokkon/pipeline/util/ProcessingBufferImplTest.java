package com.rokkon.pipeline.util;

import com.rokkon.search.model.PipeDoc; // Using PipeDoc
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

public class ProcessingBufferImplTest extends ProcessingBufferTest {

    @Override
    protected ProcessingBuffer<PipeDoc> createBuffer(int capacity, Path saveLocation) {
        return new ProcessingBufferImpl<>(capacity, PipeDoc.class, fileSystem, saveLocation, "test_prefix", 3);
    }

    @Override
    protected ProcessingBuffer<PipeDoc> createBuffer(int capacity) {
        return new ProcessingBufferImpl<>(capacity, PipeDoc.class, fileSystem);
    }

    private PipeDoc createTestPipeDoc(String id, String title) {
        return PipeDoc.newBuilder()
                .setId(id)
                .setTitle(title)
                .build();
    }

    @Test
    void saveToDisk_savesMessagesToDefaultLocation() throws IOException {
        ProcessingBuffer<PipeDoc> buffer = new ProcessingBufferImpl<>(3, PipeDoc.class, fileSystem);
        buffer.add(createTestPipeDoc("doc1", "title1"));
        buffer.add(createTestPipeDoc("doc2", "title2"));

        buffer.saveToDisk(); // Uses default path './' which maps to mockFS root

        List<Path> savedFiles = Files.list(fileSystem.getPath("./"))
                                     .filter(Files::isRegularFile)
                                     .collect(Collectors.toList());

        assertThat(savedFiles).hasSize(2);
        assertThat(savedFiles.stream().map(Path::getFileName).map(Path::toString))
            .containsExactlyInAnyOrder("protobuf-000.bin", "protobuf-001.bin");

        // Verify content
        PipeDoc loadedDoc = PipeDoc.parseFrom(Files.readAllBytes(fileSystem.getPath("./protobuf-000.bin")));
        assertThat(loadedDoc).isEqualTo(createTestPipeDoc("doc1", "title1"));
    }

    @Test
    void saveToDisk_savesMessagesToSpecifiedLocation() throws IOException {
        ProcessingBuffer<PipeDoc> buffer = createBuffer(3, mockSaveLocation);
        buffer.add(createTestPipeDoc("docA", "titleA"));
        buffer.add(createTestPipeDoc("docB", "titleB"));

        buffer.saveToDisk(mockSaveLocation, "custom_prefix", 4);

        List<Path> savedFiles = Files.list(mockSaveLocation)
                                     .filter(Files::isRegularFile)
                                     .collect(Collectors.toList());

        assertThat(savedFiles).hasSize(2);
        assertThat(savedFiles.stream().map(Path::getFileName).map(Path::toString))
            .containsExactlyInAnyOrder("custom_prefix-0000.bin", "custom_prefix-0001.bin");
    }

    @Test
    void saveToDisk_createsDirectoryIfNotExists() throws IOException {
        Path nonExistentDir = mockSaveLocation.resolve("new_dir");
        ProcessingBuffer<PipeDoc> buffer = createBuffer(1, nonExistentDir);
        buffer.add(createTestPipeDoc("doc", "title"));

        assertThat(Files.exists(nonExistentDir)).isFalse();
        buffer.saveToDisk(nonExistentDir, "test", 1);
        assertThat(Files.exists(nonExistentDir)).isTrue();
        assertThat(Files.list(nonExistentDir)).hasSize(1);
    }

    @Test
    void saveOnShutdown_savesIfBufferNotEmpty() throws IOException {
        ProcessingBufferImpl<PipeDoc> buffer = (ProcessingBufferImpl<PipeDoc>) createBuffer(2, mockSaveLocation);
        buffer.add(createTestPipeDoc("shutdown", "title"));

        // Directly call the PreDestroy method for testing purposes
        buffer.saveOnShutdown();

        List<Path> savedFiles = Files.list(mockSaveLocation)
                                     .filter(Files::isRegularFile)
                                     .collect(Collectors.toList());
        assertThat(savedFiles).hasSize(1);
        assertThat(savedFiles.get(0).getFileName().toString()).startsWith("test_prefix-");
    }

    @Test
    void saveOnShutdown_doesNothingIfBufferEmpty() throws IOException {
        ProcessingBufferImpl<PipeDoc> buffer = (ProcessingBufferImpl<PipeDoc>) createBuffer(2, mockSaveLocation);
        // Buffer is empty

        buffer.saveOnShutdown();

        assertThat(Files.list(mockSaveLocation)).isEmpty(); // No files should be saved
    }

    @Test
    void add_clonesMessage() {
        ProcessingBuffer<PipeDoc> buffer = createBuffer(1);
        PipeDoc original = PipeDoc.newBuilder().setId("original").build();
        buffer.add(original);

        // Modify the original message AFTER adding it to the buffer
        PipeDoc modifiedOriginal = original.toBuilder().setId("modified").build();
        // Since original is immutable, this creates a new instance.
        // We need to ensure the buffer copied the original's state.

        PipeDoc messageInBuffer = buffer.snapshot().get(0);
        assertThat(messageInBuffer.getId()).isEqualTo("original"); // Should still be "original"
        assertThat(messageInBuffer).isNotSameAs(original); // Should be a clone
    }

}