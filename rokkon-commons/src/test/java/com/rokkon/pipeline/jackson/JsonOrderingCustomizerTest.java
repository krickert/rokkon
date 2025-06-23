package com.rokkon.pipeline.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JsonOrderingCustomizer to ensure JSON properties are ordered correctly.
 * This is critical for consistent schema fingerprinting and comparison.
 */
@QuarkusTest
public class JsonOrderingCustomizerTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testMapEntriesAreSortedByKeys() throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("zebra", "last");
        map.put("apple", "first");
        map.put("middle", "center");
        map.put("banana", "second");

        String json = objectMapper.writeValueAsString(map);
        
        // Map entries should be sorted alphabetically by key
        assertThat(json).isEqualTo("{\"apple\":\"first\",\"banana\":\"second\",\"middle\":\"center\",\"zebra\":\"last\"}");
    }

    @Test
    void testObjectPropertiesAreSortedAlphabetically() throws Exception {
        TestObject obj = new TestObject("z-value", "a-value", "m-value", "b-value");
        
        String json = objectMapper.writeValueAsString(obj);
        
        // Object properties should be sorted alphabetically
        // Snake_case strategy just lowercases single-word properties
        assertThat(json).isEqualTo("{\"afield\":\"a-value\",\"bfield\":\"b-value\",\"mfield\":\"m-value\",\"zfield\":\"z-value\"}");
    }

    @Test
    void testNestedMapsAreSorted() throws Exception {
        Map<String, Map<String, String>> nested = new LinkedHashMap<>();
        
        Map<String, String> first = new LinkedHashMap<>();
        first.put("z", "26");
        first.put("a", "1");
        
        Map<String, String> second = new LinkedHashMap<>();
        second.put("beta", "2");
        second.put("alpha", "1");
        
        nested.put("second", second);
        nested.put("first", first);
        
        String json = objectMapper.writeValueAsString(nested);
        
        // Both outer and inner maps should be sorted
        assertThat(json).contains("\"first\":{\"a\":\"1\",\"z\":\"26\"}");
        assertThat(json).contains("\"second\":{\"alpha\":\"1\",\"beta\":\"2\"}");
        assertThat(json).matches(".*first.*second.*"); // First should come before second
    }

    @Test
    void testSchemaFingerprintConsistency() throws Exception {
        // This simulates a JSON schema where consistent ordering is critical
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> chunkSize = new LinkedHashMap<>();
        chunkSize.put("type", "integer");
        chunkSize.put("default", 1000);
        
        Map<String, Object> overlap = new LinkedHashMap<>();
        overlap.put("type", "integer");
        overlap.put("default", 100);
        
        properties.put("chunkSize", chunkSize);
        properties.put("overlap", overlap);
        
        schema.put("properties", properties);
        schema.put("required", new String[]{"chunkSize"});
        
        String json = objectMapper.writeValueAsString(schema);
        
        // The JSON should have consistent ordering for reliable fingerprinting
        assertThat(json).isEqualTo("{\"properties\":{\"chunkSize\":{\"default\":1000,\"type\":\"integer\"},\"overlap\":{\"default\":100,\"type\":\"integer\"}},\"required\":[\"chunkSize\"],\"type\":\"object\"}");
    }

    @Test
    void testCamelCaseToSnakeCase() throws Exception {
        CamelCaseBean bean = new CamelCaseBean();
        bean.setFirstName("John");
        bean.setLastName("Doe");
        bean.setEmailAddress("john@example.com");
        bean.setPhoneNumber("555-1234");
        
        String json = objectMapper.writeValueAsString(bean);
        
        // Verify camelCase is converted to snake_case for data scientist happiness
        assertThat(json).isEqualTo("{\"email_address\":\"john@example.com\",\"first_name\":\"John\",\"last_name\":\"Doe\",\"phone_number\":\"555-1234\"}");
    }

    // Test object with fields in non-alphabetical order
    static class TestObject {
        private final String zField;
        private final String aField;
        private final String mField;
        private final String bField;

        public TestObject(String zField, String aField, String mField, String bField) {
            this.zField = zField;
            this.aField = aField;
            this.mField = mField;
            this.bField = bField;
        }

        public String getZField() { return zField; }
        public String getAField() { return aField; }
        public String getMField() { return mField; }
        public String getBField() { return bField; }
    }
    
    // Test object with camelCase properties
    static class CamelCaseBean {
        private String firstName;
        private String lastName;
        private String emailAddress;
        private String phoneNumber;
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getEmailAddress() { return emailAddress; }
        public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }
}