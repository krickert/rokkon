package com.rokkon.pipeline.utils;

import com.google.protobuf.*;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A streamlined utility for dynamically mapping fields between Protocol Buffer messages.
 *
 * <p>This class refactors the original concept of a multi-component mapping system
 * (parser, resolver, executor) into a more consolidated design. The core logic is
 * encapsulated within the `FieldAccessor`, which handles both reading and writing
 * field values based on a dot-notation path, similar to a simplified object property selector.
 *
 * <p><b>Corrected Features:</b>
 * <ul>
 * <li>Handles literal value assignments (strings, booleans, numbers, null).</li>
 * <li>Correctly reads from and writes to `google.protobuf.Struct` fields, handling type conversions from DynamicMessage.</li>
 * <li>Provides precise error messages for invalid paths.</li>
 * </ul>
 */
public class ProtoFieldMapper {

    private final RuleParser ruleParser = new RuleParser();
    private final FieldAccessor fieldAccessor = new FieldAccessor();

    /**
     * Maps fields from a source message to a target message builder based on a list of rules.
     */
    public void map(Message source, Message.Builder targetBuilder, List<String> rules) throws MappingException {
        List<MappingRule> parsedRules = ruleParser.parse(rules);

        for (MappingRule rule : parsedRules) {
            try {
                Object value = null;
                // For assign/append, we need a value from the source path.
                // For clear, the source path is null and so is the value.
                if (rule.sourcePath != null) {
                    value = fieldAccessor.getValue(source, rule.sourcePath, rule.originalRule);
                }

                switch (rule.operation) {
                    case ASSIGN:
                        fieldAccessor.setValue(targetBuilder, rule.targetPath, value, rule.originalRule);
                        break;
                    case APPEND:
                        fieldAccessor.appendValue(targetBuilder, rule.targetPath, value, rule.originalRule);
                        break;
                    case CLEAR:
                        fieldAccessor.clearField(targetBuilder, rule.targetPath, rule.originalRule);
                        break;
                }
            } catch (Exception e) {
                if (e instanceof MappingException) {
                    throw e; // Re-throw if it's already our specific exception
                }
                // Wrap other exceptions with the rule that caused them.
                throw new MappingException("Failed to execute rule: " + rule.originalRule, e, rule.originalRule);
            }
        }
    }

    // --- Nested Helper Classes ---

    public static class MappingException extends Exception {
        public MappingException(String message, String rule) {
            super(message + (rule != null ? " (Rule: '" + rule + "')" : ""));
        }
        public MappingException(String message, Throwable cause, String rule) {
            super(message + (rule != null ? " (Rule: '" + rule + "')" : ""), cause);
        }
    }

    private static class MappingRule {
        final String targetPath;
        final String sourcePath;
        final Operation operation;
        final String originalRule;

        MappingRule(String targetPath, String sourcePath, Operation operation, String originalRule) {
            this.targetPath = targetPath;
            this.sourcePath = sourcePath;
            this.operation = operation;
            this.originalRule = originalRule;
        }
    }

    private enum Operation { ASSIGN, APPEND, CLEAR }

    private static class RuleParser {
        private static final Pattern ASSIGN_PATTERN = Pattern.compile("^\\s*([^=\\s]+)\\s*=\\s*(.+)\\s*$");
        private static final Pattern APPEND_PATTERN = Pattern.compile("^\\s*([^+\\s]+)\\s*\\+=\\s*(.+)\\s*$");
        private static final Pattern CLEAR_PATTERN = Pattern.compile("^\\s*-\\s*(\\S+)\\s*$");

        public List<MappingRule> parse(List<String> ruleStrings) throws MappingException {
            List<MappingRule> rules = new ArrayList<>();
            for (String ruleString : ruleStrings) {
                if (ruleString == null || ruleString.trim().isEmpty()) continue;
                Matcher assignMatcher = ASSIGN_PATTERN.matcher(ruleString);
                if (assignMatcher.matches()) {
                    rules.add(new MappingRule(assignMatcher.group(1), assignMatcher.group(2), Operation.ASSIGN, ruleString));
                    continue;
                }
                Matcher appendMatcher = APPEND_PATTERN.matcher(ruleString);
                if (appendMatcher.matches()) {
                    rules.add(new MappingRule(appendMatcher.group(1), appendMatcher.group(2), Operation.APPEND, ruleString));
                    continue;
                }
                Matcher clearMatcher = CLEAR_PATTERN.matcher(ruleString);
                if (clearMatcher.matches()) {
                    rules.add(new MappingRule(clearMatcher.group(1), null, Operation.CLEAR, ruleString));
                    continue;
                }
                throw new MappingException("Invalid rule syntax", ruleString);
            }
            return rules;
        }
    }

    static class FieldAccessor {
        private static final String PATH_SEPARATOR_REGEX = "\\.";

        public Object getValue(MessageOrBuilder root, String path, String rule) throws MappingException {
            String trimmedPath = path.trim();
            // Check for literals first.
            if (trimmedPath.equals("null")) return null;
            if (trimmedPath.equals("true")) return true;
            if (trimmedPath.equals("false")) return false;
            if (trimmedPath.startsWith("\"") && trimmedPath.endsWith("\"")) {
                if (trimmedPath.length() == 1) throw new MappingException("Invalid empty quoted string literal", rule);
                return trimmedPath.substring(1, trimmedPath.length() - 1);
            }
            try {
                if (!trimmedPath.contains(" ") && (trimmedPath.matches("-?\\d+\\.\\d+") || trimmedPath.matches("-?\\d+"))) {
                    if (trimmedPath.contains(".")) {
                        return Double.parseDouble(trimmedPath);
                    }
                    return Long.parseLong(trimmedPath);
                }
            } catch (NumberFormatException e) {
                // Not a number, so it must be a path. Proceed.
            }

            // It's a path, so resolve it.
            String[] parts = trimmedPath.split(PATH_SEPARATOR_REGEX);
            Object current = root;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (current == null) {
                    throw new MappingException("Cannot resolve path '" + path + "': intermediate value is null at segment '" + part + "'", rule);
                }

                if (current instanceof Struct) {
                    Value value = ((Struct) current).getFieldsMap().get(part);
                    if (value == null && i < parts.length - 1) {
                        throw new MappingException("Cannot resolve path '" + path + "': key '" + part + "' not found in struct", rule);
                    }
                    current = unwrapValue(value);
                } else if (current instanceof MessageOrBuilder) {
                    MessageOrBuilder currentMsg = (MessageOrBuilder) current;
                    FieldDescriptor fd = findField(currentMsg.getDescriptorForType(), part, rule);

                    if (i == parts.length - 1) {
                        if (!fd.isRepeated() && !currentMsg.hasField(fd)) return null;
                        return currentMsg.getField(fd);
                    } else {
                        if (fd.isRepeated() || fd.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                            throw new MappingException("Path '" + path + "' attempts to traverse through non-message or repeated field '" + part + "'", rule);
                        }
                        if (!currentMsg.hasField(fd)) {
                            throw new MappingException("Path '" + path + "' is invalid because intermediate field '" + part + "' is not set.", rule);
                        }
                        current = currentMsg.getField(fd);
                    }
                } else {
                    throw new MappingException("Cannot resolve path '" + path + "': tried to traverse through a non-message, non-struct value at '" + part + "'", rule);
                }
            }
            return current;
        }

        public void setValue(Message.Builder root, String path, Object value, String rule) throws MappingException {
            PathResolutionResult result = resolvePathToFinalContainer(root, path, rule);
            String fieldName = result.finalPathPart;
            Message.Builder containerBuilder = (Message.Builder) result.container;

            if (containerBuilder.getDescriptorForType().getFullName().equals(Struct.getDescriptor().getFullName())) {
                try {
                    // This is the key insight: build the generic message, then parse it back into a concrete Struct to get a proper builder.
                    Struct currentStruct = Struct.parseFrom(containerBuilder.build().toByteString());
                    Struct.Builder modifiedStructBuilder = currentStruct.toBuilder();
                    modifiedStructBuilder.putFields(fieldName, wrapValue(value));
                    containerBuilder.clear().mergeFrom(modifiedStructBuilder.build());
                } catch(InvalidProtocolBufferException e) {
                    throw new MappingException("Failed to rebuild struct for setting value", e, rule);
                }
            } else {
                FieldDescriptor fd = findField(containerBuilder.getDescriptorForType(), fieldName, rule);
                containerBuilder.setField(fd, value);
            }
        }

        public void appendValue(Message.Builder root, String path, Object value, String rule) throws MappingException {
            PathResolutionResult result = resolvePathToFinalContainer(root, path, rule);
            if (!(result.container instanceof Message.Builder)) {
                throw new MappingException("Cannot append to a non-message field", rule);
            }
            Message.Builder finalBuilder = (Message.Builder) result.container;
            FieldDescriptor fd = findField(finalBuilder.getDescriptorForType(), result.finalPathPart, rule);

            if (!fd.isRepeated()) {
                throw new MappingException("Cannot append: field '" + fd.getName() + "' is not repeated", rule);
            }

            if (value instanceof List) {
                for (Object item : (List<?>) value) finalBuilder.addRepeatedField(fd, item);
            } else {
                finalBuilder.addRepeatedField(fd, value);
            }
        }

        public void clearField(Message.Builder root, String path, String rule) throws MappingException {
            PathResolutionResult result = resolvePathToFinalContainer(root, path, rule);
            String fieldName = result.finalPathPart;
            Message.Builder containerBuilder = (Message.Builder) result.container;

            if (containerBuilder.getDescriptorForType().getFullName().equals(Struct.getDescriptor().getFullName())) {
                try {
                    Struct currentStruct = Struct.parseFrom(containerBuilder.build().toByteString());
                    Struct.Builder modifiedStructBuilder = currentStruct.toBuilder();
                    modifiedStructBuilder.removeFields(fieldName);
                    containerBuilder.clear().mergeFrom(modifiedStructBuilder.build());
                } catch(InvalidProtocolBufferException e) {
                    throw new MappingException("Failed to rebuild struct for clearing field", e, rule);
                }
            } else {
                FieldDescriptor fd = findField(containerBuilder.getDescriptorForType(), fieldName, rule);
                containerBuilder.clearField(fd);
            }
        }

        private static class PathResolutionResult {
            final Object container;
            final String finalPathPart;
            PathResolutionResult(Object container, String finalPathPart) {
                this.container = container;
                this.finalPathPart = finalPathPart;
            }
        }

        private PathResolutionResult resolvePathToFinalContainer(Message.Builder root, String path, String rule) throws MappingException {
            String[] parts = path.split(PATH_SEPARATOR_REGEX);
            Message.Builder currentBuilder = root;

            for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                FieldDescriptor fd = findField(currentBuilder.getDescriptorForType(), part, rule);

                if (fd.isRepeated() || fd.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                    throw new MappingException("Path '" + path + "' attempts to traverse through non-singular message field '" + part + "'", rule);
                }
                currentBuilder = currentBuilder.getFieldBuilder(fd);
            }
            return new PathResolutionResult(currentBuilder, parts[parts.length - 1]);
        }

        private FieldDescriptor findField(Descriptor d, String name, String fullPath) throws MappingException {
            FieldDescriptor fd = d.findFieldByName(name);
            if (fd == null) {
                throw new MappingException("Field '" + name + "' not found in message '" + d.getName() + "'", fullPath);
            }
            return fd;
        }

        private Value wrapValue(Object value) {
            if (value == null) return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
            if (value instanceof String) return Value.newBuilder().setStringValue((String) value).build();
            if (value instanceof Double) return Value.newBuilder().setNumberValue((Double) value).build();
            if (value instanceof Float) return Value.newBuilder().setNumberValue(((Float) value).doubleValue()).build();
            if (value instanceof Number) return Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build();
            if (value instanceof Boolean) return Value.newBuilder().setBoolValue((Boolean) value).build();
            if (value instanceof Struct) return Value.newBuilder().setStructValue((Struct) value).build();
            if (value instanceof List) {
                ListValue.Builder listBuilder = ListValue.newBuilder();
                for(Object item : (List<?>) value) listBuilder.addValues(wrapValue(item));
                return Value.newBuilder().setListValue(listBuilder).build();
            }
            throw new IllegalArgumentException("Cannot wrap unsupported type to Protobuf Value: " + value.getClass().getName());
        }

        private Object unwrapValue(Value value) {
            if (value == null || value.getKindCase() == Value.KindCase.NULL_VALUE) return null;
            switch(value.getKindCase()) {
                case NUMBER_VALUE: return value.getNumberValue();
                case STRING_VALUE: return value.getStringValue();
                case BOOL_VALUE: return value.getBoolValue();
                case STRUCT_VALUE: return value.getStructValue();
                case LIST_VALUE: return value.getListValue().getValuesList().stream().map(this::unwrapValue).collect(Collectors.toList());
                default: return null;
            }
        }
    }
}
