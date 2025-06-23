package com.rokkon.pipeline.engine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.ListValue;

import java.util.Map;

/**
 * Utility class for converting between Jackson JsonNode and Protobuf Struct.
 */
public class JsonProtoConverter {
    
    private JsonProtoConverter() {
        // Utility class
    }
    
    /**
     * Convert a Jackson JsonNode to a Protobuf Struct.
     */
    public static Struct jsonNodeToStruct(JsonNode node) {
        if (node == null || node.isNull()) {
            return Struct.getDefaultInstance();
        }
        
        Struct.Builder structBuilder = Struct.newBuilder();
        
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                structBuilder.putFields(entry.getKey(), jsonNodeToValue(entry.getValue()));
            });
        }
        
        return structBuilder.build();
    }
    
    /**
     * Convert a Jackson JsonNode to a Protobuf Value.
     */
    private static Value jsonNodeToValue(JsonNode node) {
        Value.Builder valueBuilder = Value.newBuilder();
        
        if (node.isNull()) {
            valueBuilder.setNullValue(com.google.protobuf.NullValue.NULL_VALUE);
        } else if (node.isBoolean()) {
            valueBuilder.setBoolValue(node.booleanValue());
        } else if (node.isNumber()) {
            if (node.isIntegralNumber()) {
                valueBuilder.setNumberValue(node.longValue());
            } else {
                valueBuilder.setNumberValue(node.doubleValue());
            }
        } else if (node.isTextual()) {
            valueBuilder.setStringValue(node.textValue());
        } else if (node.isArray()) {
            ListValue.Builder listBuilder = ListValue.newBuilder();
            node.forEach(item -> listBuilder.addValues(jsonNodeToValue(item)));
            valueBuilder.setListValue(listBuilder.build());
        } else if (node.isObject()) {
            valueBuilder.setStructValue(jsonNodeToStruct(node));
        }
        
        return valueBuilder.build();
    }
}