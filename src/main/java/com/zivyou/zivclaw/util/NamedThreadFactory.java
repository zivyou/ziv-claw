package com.zivyou.zivclaw.util;

import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class NamedThreadFactory implements ThreadFactory {
    private final AtomicInteger number = new AtomicInteger(1);
    private final String prefix;
    @Override
    public Thread newThread(@Nonnull Runnable runnable) {
        return new Thread(runnable, prefix + "-" + number.getAndIncrement());
    }
}
