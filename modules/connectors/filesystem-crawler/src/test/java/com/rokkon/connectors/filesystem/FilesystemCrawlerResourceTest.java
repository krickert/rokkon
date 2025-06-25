package com.rokkon.connectors.filesystem;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;

@QuarkusTest
public class FilesystemCrawlerResourceTest {

    @Inject
    FilesystemCrawlerResource resource;

    @InjectMock
    FilesystemCrawlerConnector connector;

    @Test
    void testGetStatus() {
        // Configure the mock connector
        when(connector.rootPath).thenReturn("/tmp/test");
        when(connector.fileExtensions).thenReturn("txt,md,json");
        when(connector.maxFileSize).thenReturn(1024L * 1024L);
        when(connector.includeHidden).thenReturn(false);
        when(connector.maxDepth).thenReturn(10);
        when(connector.batchSize).thenReturn(100);
        when(connector.deleteOrphans).thenReturn(true);

        // Test the status endpoint
        given()
            .when()
                .get("/api/crawler/status")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("rootPath", is("/tmp/test"))
                .body("fileExtensions", is("txt,md,json"))
                .body("maxFileSize", is(1048576))
                .body("includeHidden", is(false))
                .body("maxDepth", is(10))
                .body("batchSize", is(100))
                .body("deleteOrphans", is(true));
    }

    @Test
    void testTriggerCrawl() {
        // Configure the mock connector
        doNothing().when(connector).crawl();

        // Test the crawl endpoint
        given()
            .when()
                .post("/api/crawler/crawl")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("started"))
                .body("message", containsString("Crawl started successfully"));

        // Verify that the crawl method was called
        // Note: Since we're starting the crawl in a separate thread, we need to wait a bit
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore
        }

        // Verify that the crawl method was called
        verify(connector, timeout(1000)).crawl();
    }

    @Test
    void testTriggerCrawlWithPath() throws Exception {
        // Create a temporary directory for testing
        Path tempDir = Files.createTempDirectory("crawler-test");
        try {
            // Configure the mock connector
            doNothing().when(connector).crawl();

            // Test the crawl endpoint with a custom path
            given()
                .when()
                    .post("/api/crawler/crawl/" + tempDir.toString())
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("status", is("started"))
                    .body("message", containsString("Crawl started successfully"));

            // Verify that the root path was temporarily changed
            verify(connector, timeout(1000)).rootPath = tempDir.toString();

            // Verify that the crawl method was called
            verify(connector, timeout(1000)).crawl();
        } finally {
            // Clean up
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testTriggerCrawlWithInvalidPath() {
        // Test the crawl endpoint with an invalid path
        String invalidPath = "/path/that/does/not/exist";

        given()
            .when()
                .post("/api/crawler/crawl/" + invalidPath)
            .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("status", is("error"))
                .body("message", containsString("Root path does not exist"));

        // Verify that the crawl method was not called
        verify(connector, never()).crawl();
    }
}
