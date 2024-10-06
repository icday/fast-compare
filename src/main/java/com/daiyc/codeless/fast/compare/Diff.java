package com.daiyc.codeless.fast.compare;

/**
 * @author daiyc
 * @since 2024/10/4
 */
public interface Diff<T> {
    T getOriginal();

    T getCurrent();
}
