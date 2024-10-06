package com.daiyc.codeless.fast.compare;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Data
@RequiredArgsConstructor
public class ValueDiff<T> implements Diff<T> {
    private final Class<T> type;

    private final T original;

    private final T current;
}
