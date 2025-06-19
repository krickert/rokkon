# Chunking Strategies Architecture - TODO

## Overview
Implement multiple chunking strategies to handle different use cases and fix UTF-8 encoding issues. The chunker module should support configurable strategies via JSON schema (V7 compliant).

## Proposed Architecture

### 1. ChunkerStrategy Interface
```java
public interface ChunkerStrategy {
    ChunkingResult createChunks(PipeDoc document, ChunkerOptions options, String streamId, String pipeStepName);
    String getStrategyName();
    boolean supportsCharacterOffsets();
}
```

### 2. Chunking Strategy Enum (JSON Schema V7 Compatible)
```java
public enum ChunkingStrategyType {
    OPENNLP_TOKEN("opennlp_token", "Current OpenNLP token-based chunking"),
    DJL_TOKEN("djl_token", "DJL HuggingFace tokenizer-based chunking (UTF-8 safe)"),
    SEMANTIC("semantic", "Embedding-based semantic chunking using similarity"),
    CHARACTER("character", "Simple character-based chunking with overlap");
}
```

### 3. Strategy Implementations

#### a) OpenNLPTokenChunker (Current Implementation)
- **Status**: Implemented but has UTF-8 surrogate pair issues
- **Use Case**: Legacy support, fast token-based chunking
- **Position Tracking**: Yes, via tokenizePos()

#### b) DJLTokenChunker (Priority Fix for UTF-8)
- **Status**: To be implemented
- **Use Case**: Drop-in replacement for OpenNLP, fixes UTF-8 issues
- **Benefits**: 
  - Rust-based tokenizers via JNI handle Unicode properly
  - Access to modern tokenizers (BERT, GPT, etc.)
  - Maintains position tracking via CharSpan
- **Implementation**:
```java
// Use existing DJL dependency from embedder
implementation("ai.djl.huggingface:tokenizers:0.33.0")

// Adapter pattern to match OpenNLP API
public class DJLTokenChunker implements ChunkerStrategy {
    private final HuggingFaceTokenizer tokenizer;
    
    public DJLTokenChunker(String tokenizerName) {
        this.tokenizer = HuggingFaceTokenizer.builder()
            .optTokenizerName(tokenizerName)
            .build();
    }
}
```

#### c) SemanticChunker
- **Status**: To be implemented
- **Use Case**: High-quality semantic boundaries for RAG
- **Benefits**:
  - Creates semantically coherent chunks
  - Better retrieval quality
  - Respects topic boundaries
- **Implementation Approach**:
  1. Use sentence detector (OpenNLP or DJL)
  2. Generate embeddings per sentence (call embedder service)
  3. Calculate cosine similarity between consecutive sentences
  4. Break at significant similarity drops
  5. Maintain character offsets throughout

#### d) CharacterBasedChunker
- **Status**: To be implemented
- **Use Case**: Simple fallback, predictable behavior
- **Benefits**:
  - Fast and simple
  - No tokenization complexity
  - Predictable chunk sizes
- **Implementation**: Simple substring with overlap

### 4. Configuration Schema (JSON Schema V7)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "chunking_strategy": {
      "type": "string",
      "enum": ["opennlp_token", "djl_token", "semantic", "character"],
      "default": "djl_token",
      "description": "The chunking strategy to use"
    },
    "source_field": {
      "type": "string",
      "default": "body"
    },
    "chunk_size": {
      "type": "integer",
      "minimum": 50,
      "maximum": 10000,
      "default": 500
    },
    "chunk_overlap": {
      "type": "integer",
      "minimum": 0,
      "maximum": 1000,
      "default": 50
    },
    "preserve_urls": {
      "type": "boolean",
      "default": true
    },
    "tokenizer_model": {
      "type": "string",
      "default": "bert-base-uncased",
      "description": "For DJL token strategy - which tokenizer to use"
    },
    "similarity_threshold_percentile": {
      "type": "number",
      "minimum": 0,
      "maximum": 100,
      "default": 80,
      "description": "For semantic strategy - percentile for similarity threshold"
    }
  },
  "required": ["chunking_strategy", "source_field", "chunk_size", "chunk_overlap"]
}
```

### 5. Factory Pattern for Strategy Selection
```java
@Singleton
public class ChunkerStrategyFactory {
    @Inject OpenNLPTokenChunker openNLPChunker;
    @Inject DJLTokenChunker djlTokenChunker;
    @Inject SemanticChunker semanticChunker;
    @Inject CharacterBasedChunker characterChunker;
    
    public ChunkerStrategy getStrategy(ChunkingStrategyType type) {
        return switch (type) {
            case OPENNLP_TOKEN -> openNLPChunker;
            case DJL_TOKEN -> djlTokenChunker;
            case SEMANTIC -> semanticChunker;
            case CHARACTER -> characterChunker;
        };
    }
}
```

### 6. Integration Points

#### Text Analysis Preservation
All strategies must maintain:
- Character offsets (start/end positions)
- Token counts where applicable
- URL preservation capability
- Chunk overlap tracking

#### Error Handling
- Graceful fallback: If semantic fails, fall back to DJL token
- Log detailed errors with document IDs
- Track failure reasons in ProcessResponse logs
- UTF-8 cleaning as last resort (not first)

### 7. Testing Requirements
- Test all strategies with:
  - Unicode text (emojis, special characters)
  - Large documents (>1MB)
  - Documents with URLs
  - Empty/null text
  - Single sentence documents
- Performance benchmarks for each strategy
- Accuracy metrics for semantic chunking

### 8. Implementation Priority
1. **Immediate**: Add graceful error handling to current chunker
2. **Next Sprint**: Implement DJL token chunker (fixes UTF-8)
3. **Following**: Add semantic chunker (quality improvement)
4. **Nice to Have**: Character-based chunker (fallback)

### 9. Benefits of This Architecture
- **Pluggable**: Easy to add new strategies
- **Configurable**: Runtime selection via JSON config
- **Testable**: Each strategy can be tested independently
- **Maintainable**: Clear separation of concerns
- **Future-proof**: Can add ML-based chunkers, language-specific chunkers, etc.

### 10. Cross-Language Alternatives (For Future Consideration)
Since we're on gRPC, we could implement high-performance chunkers in:
- **Rust**: Using `text-splitter` crate (fastest)
- **Python**: Using LangChain (most features)
- **Go**: Native gRPC support (good middle ground)

Each would be a separate gRPC service that implements PipeStepProcessor.

## Next Steps
1. Continue with embedder module testing
2. Return to implement this architecture after embedder is working
3. Start with DJL token chunker to fix UTF-8 issues
4. Add semantic chunking for quality improvement

## Notes
- This is an early, critical component - worth investing time
- Affects downstream quality of embeddings and search
- Should be implemented before production deployment