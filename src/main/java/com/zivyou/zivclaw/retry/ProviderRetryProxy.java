package com.zivyou.zivclaw.retry;

import com.zivyou.zivclaw.exception.RetryableException;
import com.zivyou.zivclaw.provider.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Slf4j
@RequiredArgsConstructor
public class ProviderRetryProxy implements InvocationHandler {
    private final Provider delegate;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (!"generate".equals(method.getName())) {
            return method.invoke(delegate, args);
        }
        Retry annotation = delegate.getClass().getMethod(method.getName(), method.getParameterTypes()).getAnnotation(Retry.class);
        if (null == annotation) {
            return method.invoke(delegate, args);
        }
        return proxyCall(annotation, method, args);
    }

    private Object proxyCall(Retry retry, Method method, Object[] args) throws Throwable {
        int retryTimes = retry.maxRetries();
        int attempt = 0;
        while (attempt < retryTimes) {
            try {
                return method.invoke(delegate, args);
            } catch (Throwable throwable) {
                Throwable cause = (throwable instanceof InvocationTargetException) ? throwable.getCause() : throwable;
                if (!canRetry(retry, cause, attempt)) {
                    log.error("retry exhausted or not retryable: {} (attempt {} of {})",
                            cause.getClass().getSimpleName(), attempt + 1, retryTimes, throwable);
                    throw cause;
                }
                long backoff = backoff(retry, attempt);
                log.warn("retry {}/{} for {} in {}ms: {}",
                        attempt + 1, retryTimes, cause.getClass().getSimpleName(), backoff, cause.getMessage());
                sleep(backoff);
            }
            attempt++;
        }
        throw new RuntimeException(String.format("retry times %d/%d failed", attempt, retryTimes));
    }

    /**
     * 决定某次调用失败后是否还值得重试：
     * <ul>
     *   <li>已是最后一次机会，直接放弃，不再等待退避；</li>
     *   <li>命中 ignoreOn 的异常一律不重试，即使它也命中 retryOn；</li>
     *   <li>命中 retryOn 才重试，否则放弃。</li>
     * </ul>
     */
    private boolean canRetry(Retry retry, Throwable cause, int attempt) {
        boolean lastAttempt = attempt >= retry.maxRetries() - 1;
        if (lastAttempt || isIgnored(retry.ignoreOn(), cause)) {
            return false;
        }
        return isRetryAble(retry.retryOn(), cause);
    }

    private static boolean isIgnored(Class<? extends Throwable>[] ignored, Throwable throwable) {
        return matchesAny(ignored, throwable);
    }

    private static boolean isRetryAble(Class<? extends Throwable>[] retryOn, Throwable throwable) {
        // retryOn 为空时保持默认语义：只重试 RetryableException
        if (retryOn.length == 0) {
            return RetryableException.class.isInstance(throwable);
        }
        return matchesAny(retryOn, throwable);
    }

    private static boolean matchesAny(Class<? extends Throwable>[] classes, Throwable throwable) {
        for (Class<? extends Throwable> c : classes) {
            if (c.isInstance(throwable)) {
                return true;
            }
        }
        return false;
    }

    /** 指数退避：delay * 2^attempt，封顶 maxDelay；attempt 从 0 开始，因此首次等待 delay */
    private static long backoff(Retry retry, int attempt) {
        long multiplier = (attempt == 0) ? 1L : (1L << Math.min(attempt, 30)); // 防溢出
        return Math.min(retry.delay() * multiplier, retry.maxDelay());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while retrying", e);
        }
    }
}