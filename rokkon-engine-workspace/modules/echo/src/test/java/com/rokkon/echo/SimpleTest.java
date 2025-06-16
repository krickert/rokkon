package com.rokkon.echo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class SimpleTest {

    @Test
    void testBasicAssertion() {
        assertThat("hello").isEqualTo("hello");
    }
}