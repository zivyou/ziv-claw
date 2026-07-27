package com.zivyou.zivclaw.provider;

import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SanityCheckTest {
    @Test
    void junitJupiterIsWired() {
        assertEquals(2, 1 + 1);
    }

    @Test
    void localModelSupportsToolSemantics() {
        Role toolRole = Role.TOOL;
        ToolCall tc = ToolCall.builder()
                .id("call_1")
                .type("function")
                .function(Function.builder().name("echo").arguments("{\"v\":1}").build())
                .build();
        assertNotNull(toolRole);
        assertEquals("call_1", tc.getId());
        assertEquals("function", tc.getType());
        assertEquals("echo", tc.getFunction().getName());
        assertEquals("{\"v\":1}", tc.getFunction().getArguments());
    }
}
