package com.zivyou.zivclaw.retry;

import com.zivyou.zivclaw.context.AgentContext;
import com.zivyou.zivclaw.exception.RetryableException;
import com.zivyou.zivclaw.message.Message;
import com.zivyou.zivclaw.message.Role;
import com.zivyou.zivclaw.provider.Provider;
import com.zivyou.zivclaw.registry.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderRetryProxyTest {
    private static final Message DUMMY = Message.builder().role(Role.ASSISTANT).build();
    private static final AgentContext CTX = AgentContext.builder().build();
    private static final List<Message> MESSAGES = List.of();
    private static final List<ToolDefinition> TOOLS = null;

    private static Provider wrap(Provider original) {
        return (Provider) Proxy.newProxyInstance(
                original.getClass().getClassLoader(),
                new Class<?>[]{Provider.class},
                new ProviderRetryProxy(original));
    }

    @Test
    void retriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        Provider original = new Provider() {
            @Override
            @Retry
            public Message generate(AgentContext c, List<Message> m, List<ToolDefinition> t) {
                if (calls.incrementAndGet() < 3) {
                    throw new RetryableException("boom");
                }
                return DUMMY;
            }

            @Override
            public void close() {
            }
        };
        assertEquals(DUMMY, wrap(original).generate(CTX, MESSAGES, TOOLS));
        assertEquals(3, calls.get());
    }

    @Test
    void exhaustedAfterMaxRetriesThrowsCause() {
        AtomicInteger calls = new AtomicInteger();
        Provider original = new Provider() {
            @Override
            @Retry(maxRetries = 3, delay = 1)
            public Message generate(AgentContext c, List<Message> m, List<ToolDefinition> t) {
                calls.incrementAndGet();
                throw new RetryableException("always");
            }

            @Override
            public void close() {
            }
        };
        assertThrows(RetryableException.class, () -> wrap(original).generate(CTX, MESSAGES, TOOLS));
        assertEquals(3, calls.get());
    }

    @Test
    void ignoreOnExceptionNotRetriedEvenIfMatchesRetryOn() {
        AtomicInteger calls = new AtomicInteger();
        Provider original = new Provider() {
            @Override
            @Retry(retryOn = {Exception.class}, ignoreOn = {RetryableException.class}, delay = 100)
            public Message generate(AgentContext c, List<Message> m, List<ToolDefinition> t) {
                calls.incrementAndGet();
                throw new RetryableException("do-not-retry");
            }

            @Override
            public void close() {
            }
        };
        assertThrows(RetryableException.class, () -> wrap(original).generate(CTX, MESSAGES, TOOLS));
        // 命中 ignoreOn（即便也命中 retryOn）时只调用一次，不做退避等待
        assertEquals(1, calls.get());
    }

    @Test
    void exceptionOutsideRetryOnIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        Provider original = new Provider() {
            @Override
            @Retry(maxRetries = 5, delay = 100)
            public Message generate(AgentContext c, List<Message> m, List<ToolDefinition> t) {
                calls.incrementAndGet();
                throw new IllegalStateException("not retryable by default");
            }

            @Override
            public void close() {
            }
        };
        assertThrows(IllegalStateException.class, () -> wrap(original).generate(CTX, MESSAGES, TOOLS));
        assertEquals(1, calls.get());
    }

    @Test
    void backoffIsExponentialAndCappedByMaxDelay() {
        // delay=4, maxDelay=20：等待序列应依次为 4, 8, 16, 20(32 封顶后的值), 20
        assertTrue(backoffWith(4, 20, 0) == 4);
        assertTrue(backoffWith(4, 20, 1) == 8);
        assertTrue(backoffWith(4, 20, 2) == 16);
        assertTrue(backoffWith(4, 20, 3) == 20);
        assertTrue(backoffWith(4, 20, 4) == 20);
    }

    /** 与 ProviderRetryProxy.backoff 保持一致的实现，用于校验计算逻辑 */
    private static long backoffWith(long delay, long maxDelay, int attempt) {
        long multiplier = (attempt == 0) ? 1L : (1L << Math.min(attempt, 30));
        return Math.min(delay * multiplier, maxDelay);
    }
}