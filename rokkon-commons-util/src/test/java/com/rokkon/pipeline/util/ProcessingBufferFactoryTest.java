package com.rokkon.pipeline.util;

import com.rokkon.search.model.PipeDoc; // Using PipeDoc
import org.junit.jupiter.api.Test;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import static org.assertj.core.api.Assertions.assertThat;

public class ProcessingBufferFactoryTest {

    @Test
    void createBuffer_enabled_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, 10, PipeDoc.class, FileSystems.getDefault(), Paths.get("/tmp"), "prefix", 3);
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
        assertThat(buffer).isNotInstanceOf(NoOpProcessingBuffer.class);
    }

    @Test
    void createBuffer_disabled_returnsNoOpProcessingBuffer() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                false, 10, PipeDoc.class, FileSystems.getDefault(), Paths.get("/tmp"), "prefix", 3);
        assertThat(buffer).isInstanceOf(NoOpProcessingBuffer.class);
        assertThat(buffer).isNotInstanceOf(ProcessingBufferImpl.class);
    }

    @Test
    void createBuffer_enabledWithDefaultFS_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, 10, PipeDoc.class, Paths.get("/tmp"), "prefix", 3);
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
    }

    @Test
    void createBuffer_enabledWithDefaultSaveParams_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, 10, PipeDoc.class, FileSystems.getDefault());
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
    }

    @Test
    void createBuffer_enabledWithOnlyCapacityAndClass_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, 10, PipeDoc.class);
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
    }

    @Test
    void createBuffer_enabledWithDefaultCapacityAndClass_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, PipeDoc.class);
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
    }

    @Test
    void createBuffer_enabledWithDefaultCapacityAndClassAndFS_returnsProcessingBufferImpl() {
        ProcessingBuffer<PipeDoc> buffer = ProcessingBufferFactory.createBuffer(
                true, PipeDoc.class, FileSystems.getDefault());
        assertThat(buffer).isInstanceOf(ProcessingBufferImpl.class);
    }
}