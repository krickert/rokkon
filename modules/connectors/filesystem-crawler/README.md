# Filesystem Crawler Connector

A connector for the Rokkon Engine that crawls a filesystem path and sends documents to the engine for processing.

## Features

- Crawls a filesystem path recursively
- Filters files by extension, size, and hidden status
- Processes files in batches
- Detects and handles orphaned files (files that were processed in a previous crawl but no longer exist)
- Provides REST endpoints for status and manual triggering
- Includes health checks for monitoring

## Configuration

The connector is configured through application properties or environment variables:

| Property | Environment Variable | Default | Description |
|----------|---------------------|---------|-------------|
| `filesystem-crawler.root-path` | `CRAWLER_ROOT_PATH` | `/tmp/crawler-data` | Root path to crawl |
| `filesystem-crawler.connector-type` | - | `filesystem-crawler` | Connector type identifier |
| `filesystem-crawler.connector-id` | `CONNECTOR_ID` | `filesystem-crawler-1` | Unique connector ID |
| `filesystem-crawler.file-extensions` | `CRAWLER_FILE_EXTENSIONS` | `txt,md,json,xml,html,csv` | File extensions to process (comma-separated) |
| `filesystem-crawler.max-file-size` | `CRAWLER_MAX_FILE_SIZE` | `10485760` (10MB) | Maximum file size to process in bytes |
| `filesystem-crawler.include-hidden` | `CRAWLER_INCLUDE_HIDDEN` | `false` | Whether to include hidden files and directories |
| `filesystem-crawler.max-depth` | `CRAWLER_MAX_DEPTH` | `10` | Maximum depth to crawl (0 means no limit) |
| `filesystem-crawler.batch-size` | `CRAWLER_BATCH_SIZE` | `100` | Batch size for processing files |
| `filesystem-crawler.delete-orphans` | `CRAWLER_DELETE_ORPHANS` | `false` | Whether to delete orphaned files |
| `filesystem-crawler.engine.host` | `ENGINE_HOST` | `localhost` | Rokkon Engine host |
| `filesystem-crawler.engine.port` | `ENGINE_PORT` | `49000` | Rokkon Engine port |

## REST API

The connector provides the following REST endpoints:

### Get Status

```
GET /api/crawler/status
```

Returns the current status and configuration of the crawler.

### Trigger Crawl

```
POST /api/crawler/crawl
```

Triggers a crawl of the configured root path.

### Trigger Crawl with Custom Path

```
POST /api/crawler/crawl/{rootPath}
```

Triggers a crawl of the specified path.

## Health Checks

The connector provides health checks that can be accessed at:

```
GET /q/health
GET /q/health/live
GET /q/health/ready
```

The health checks verify that the root path exists and is readable.

## Building and Running

### Building

```bash
./gradlew :modules:connectors:filesystem-crawler:build
```

### Running

```bash
java -jar modules/connectors/filesystem-crawler/build/quarkus-app/quarkus-run.jar
```

Or with custom configuration:

```bash
java -DCRAWLER_ROOT_PATH=/path/to/data -DENGINE_HOST=engine.example.com -jar modules/connectors/filesystem-crawler/build/quarkus-app/quarkus-run.jar
```

## Docker

### Building the Docker Image

```bash
./gradlew :modules:connectors:filesystem-crawler:build
docker build -f modules/connectors/filesystem-crawler/src/main/docker/Dockerfile.jvm -t rokkon/filesystem-crawler .
```

### Running the Docker Container

```bash
docker run -p 8080:8080 \
  -e CRAWLER_ROOT_PATH=/data \
  -e ENGINE_HOST=engine.example.com \
  -e ENGINE_PORT=49000 \
  -v /path/on/host:/data \
  rokkon/filesystem-crawler
```

## User Interface

The connector provides a simple web-based user interface that can be accessed at the root URL (e.g., `http://localhost:8080/`). The UI allows you to:

- View the current status and configuration of the crawler
- Trigger crawls with the default or a custom path
- Access the Swagger UI for API documentation

## Popular File Crawlers and Comparison

There are several popular file crawling solutions available, each with different features and use cases. Here's how our filesystem crawler compares to some of them:

### Apache Nutch

[Apache Nutch](https://nutch.apache.org/) is a highly extensible and scalable web crawler that can be adapted for filesystem crawling.

**Comparison:**
- Nutch is more complex and feature-rich, designed primarily for web crawling
- Our crawler is lightweight and specifically designed for filesystem crawling
- Nutch requires more configuration and setup
- Our crawler integrates directly with the Rokkon Engine

### Apache Tika

[Apache Tika](https://tika.apache.org/) is a content analysis toolkit that can detect and extract metadata and text from various document types.

**Comparison:**
- Tika focuses on content extraction and analysis rather than crawling
- Our crawler can be combined with Tika (in the pipeline) for content extraction
- Tika supports a wider range of document formats
- Our crawler is optimized for integration with the Rokkon Engine

### Elasticsearch File System Crawler

The [Elasticsearch File System Crawler](https://github.com/dadoonet/fscrawler) is a tool that crawls a file system and indexes documents in Elasticsearch.

**Comparison:**
- Both crawlers support recursive directory traversal and file filtering
- Elasticsearch FSCrawler is specifically designed for Elasticsearch
- Our crawler is more flexible and can send documents to any system via the Rokkon Engine
- Our crawler supports orphan detection and handling

### Apache Camel File Component

[Apache Camel](https://camel.apache.org/components/latest/file-component.html) provides a file component that can be used for file crawling and processing.

**Comparison:**
- Camel provides a more general-purpose integration framework
- Our crawler is specifically designed for the Rokkon Engine
- Camel requires more configuration but offers more flexibility
- Our crawler is simpler to set up and use for the specific use case

### Custom Solutions

Many organizations build custom file crawlers tailored to their specific needs using libraries like Java NIO, Apache Commons IO, or similar tools in other languages.

**Comparison:**
- Custom solutions can be highly optimized for specific use cases
- Our crawler provides a ready-to-use solution that integrates with the Rokkon Engine
- Custom solutions require development and maintenance effort
- Our crawler is maintained as part of the Rokkon ecosystem

## References and Further Reading

- [Apache Commons IO](https://commons.apache.org/proper/commons-io/) - The library used by our crawler for file operations
- [Java NIO.2 File API](https://docs.oracle.com/javase/tutorial/essential/io/fileio.html) - The underlying Java API for file operations
- [Quarkus Guides](https://quarkus.io/guides/) - Documentation for the Quarkus framework used by our crawler
- [gRPC Documentation](https://grpc.io/docs/) - Information about the gRPC protocol used for communication with the Rokkon Engine
- [OpenAPI Specification](https://swagger.io/specification/) - The standard used for our API documentation

## Integration with Rokkon Engine

The filesystem crawler connector integrates with the Rokkon Engine by:

1. Crawling the filesystem and finding files that match the configured criteria
2. Creating a PipeDoc for each file, including the file content as a Blob
3. Sending the PipeDoc to the engine via gRPC
4. Handling orphaned files by sending delete requests to the engine

The connector uses the `ConnectorEngine.processConnectorDoc` RPC defined in `connector_service.proto`.
