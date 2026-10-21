package com.zivyou.zivclaw.retry;

import com.zivyou.zivclaw.exception.RetryableException;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Retry {

    Class<? extends Throwable>[] retryOn() default {RetryableException.class};
    Class<? extends Throwable>[] ignoreOn() default {};

    int maxRetries() default 3;
    long delay() default 1000; // ms
    long maxDelay() default 10000;
}
