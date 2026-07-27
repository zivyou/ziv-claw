package com.zivyou.zivclaw.provider;

import com.volcengine.ark.runtime.model.completion.chat.ChatFunctionCall;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.completion.chat.ChatToolCall;
import com.zivyou.zivclaw.message.Function;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.message.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArkProviderConvertTest {

    // --- Ark -> local ---

    @Test
    void arkToLocal_mapsPlainAssistantMessage() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .content("hello")
                .name("bot")
                .build();

        Message out = ArkProvider.convert(src);

        assertEquals(Role.ASSISTANT, out.getRole());
        assertEquals("hello", out.getContent());
        assertEquals("bot", out.getName());
        assertNull(out.getToolCalls());
        assertNull(out.getToolCallId());
        assertNull(out.getRefusal());
    }

    @Test
    void arkToLocal_mapsAllSupportedRoles() {
        assertEquals(Role.SYSTEM,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("s").build()).getRole());
        assertEquals(Role.USER,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.USER).content("u").build()).getRole());
        assertEquals(Role.ASSISTANT,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content("a").build()).getRole());
        assertEquals(Role.TOOL,
                ArkProvider.convert(ChatMessage.builder().role(ChatMessageRole.TOOL).content("t").toolCallId("call_1").build()).getRole());
    }

    @Test
    void arkToLocal_mapsToolResultWithToolCallId() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.TOOL)
                .toolCallId("call_42")
                .content("42")
                .build();

        Message out = ArkProvider.convert(src);

        assertEquals(Role.TOOL, out.getRole());
        assertEquals("call_42", out.getToolCallId());
        assertEquals("42", out.getContent());
    }

    @Test
    void arkToLocal_mapsAssistantToolCalls() {
        ChatToolCall tc = new ChatToolCall("call_1", "function", new ChatFunctionCall("echo", "{\"v\":1}"));
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();

        Message out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertEquals(1, out.getToolCalls().size());
        ToolCall got = out.getToolCalls().get(0);
        assertEquals("call_1", got.getId());
        assertEquals("function", got.getType());
        assertEquals("echo", got.getFunction().getName());
        assertEquals("{\"v\":1}", got.getFunction().getArguments());
    }

    @Test
    void arkToLocal_preservesEmptyToolCallsList() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .content("")
                .toolCalls(List.of())
                .build();

        Message out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertTrue(out.getToolCalls().isEmpty());
    }

    @Test
    void arkToLocal_rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert((ChatMessage) null));
    }

    @Test
    void arkToLocal_rejectsNullRole() {
        ChatMessage src = ChatMessage.builder().content("x").build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsLegacyFunctionRole() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.FUNCTION)
                .content("x")
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsLegacyFunctionCall() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .functionCall(new ChatFunctionCall("legacy", "{}"))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsNonStringContent() {
        ChatMessage src = ChatMessage.builder().role(ChatMessageRole.USER).build();
        src.setContent(List.of("multimodal-part"));
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsNullToolCallElement() {
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(java.util.Collections.singletonList(null))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void arkToLocal_rejectsToolCallWithoutFunction() {
        ChatToolCall tc = new ChatToolCall("call_1", "function", null);
        ChatMessage src = ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    // --- local -> Ark ---

    @Test
    void localToArk_mapsPlainAssistantMessage() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("hi")
                .name("bot")
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertEquals(ChatMessageRole.ASSISTANT, out.getRole());
        assertEquals("hi", out.getContent());
        assertEquals("bot", out.getName());
        assertNull(out.getToolCalls());
        assertNull(out.getToolCallId());
        assertNull(out.getFunctionCall());
    }

    @Test
    void localToArk_mapsAllSupportedRoles() {
        assertEquals(ChatMessageRole.SYSTEM,
                ArkProvider.convert(Message.builder().role(Role.SYSTEM).content("s").build()).getRole());
        assertEquals(ChatMessageRole.USER,
                ArkProvider.convert(Message.builder().role(Role.USER).content("u").build()).getRole());
        assertEquals(ChatMessageRole.ASSISTANT,
                ArkProvider.convert(Message.builder().role(Role.ASSISTANT).content("a").build()).getRole());
        assertEquals(ChatMessageRole.TOOL,
                ArkProvider.convert(Message.builder().role(Role.TOOL).content("t").toolCallId("call_1").build()).getRole());
    }

    @Test
    void localToArk_mapsToolResultWithToolCallId() {
        Message src = Message.builder()
                .role(Role.TOOL)
                .toolCallId("call_9")
                .content("done")
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertEquals(ChatMessageRole.TOOL, out.getRole());
        assertEquals("call_9", out.getToolCallId());
        assertEquals("done", out.getContent());
    }

    @Test
    void localToArk_mapsAssistantToolCalls() {
        ToolCall tc = ToolCall.builder()
                .id("call_1")
                .type("function")
                .function(Function.builder().name("echo").arguments("{\"v\":1}").build())
                .build();
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertEquals(1, out.getToolCalls().size());
        ChatToolCall got = out.getToolCalls().get(0);
        assertEquals("call_1", got.getId());
        assertEquals("function", got.getType());
        assertNotNull(got.getFunction());
        assertEquals("echo", got.getFunction().getName());
        assertEquals("{\"v\":1}", got.getFunction().getArguments());
    }

    @Test
    void localToArk_preservesEmptyToolCallsList() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("")
                .toolCalls(List.of())
                .build();

        ChatMessage out = ArkProvider.convert(src);

        assertNotNull(out.getToolCalls());
        assertTrue(out.getToolCalls().isEmpty());
    }

    @Test
    void localToArk_rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert((Message) null));
    }

    @Test
    void localToArk_rejectsNullRole() {
        Message src = Message.builder().content("x").build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsNonNullRefusal() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .content("x")
                .refusal("nope")
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsNullToolCallElement() {
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(java.util.Collections.singletonList(null))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }

    @Test
    void localToArk_rejectsToolCallWithoutFunction() {
        ToolCall tc = ToolCall.builder().id("call_1").type("function").build();
        Message src = Message.builder()
                .role(Role.ASSISTANT)
                .toolCalls(List.of(tc))
                .build();
        assertThrows(IllegalArgumentException.class, () -> ArkProvider.convert(src));
    }
}
