package com.daiyc.codeless.fast.compare;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@RequiredArgsConstructor
public class CollectionDiff<T> implements Diff<Collection<T>> {
    @Getter
    private final Class<? extends Collection> collectionType;

    @Getter
    private final Class<T> elementType;

    @Getter
    private final Collection<T> original;

    @Getter
    private final Collection<T> current;

    private final transient Map<String, List<T>> cache = new ConcurrentHashMap<>();

    public List<T> getAddedValues() {
        return cache.computeIfAbsent("added", key -> handle((org, cur) -> {
            Set<T> origin = new HashSet<>(org);
            return cur.stream().filter(e -> !origin.contains(e)).collect(Collectors.toList());
        }));
    }

    public List<T> getRemovedValues() {
        return cache.computeIfAbsent("removed", key -> handle((org, cur) -> {
            Set<T> curSet = new HashSet<>(cur);
            return org.stream().filter(e -> !curSet.contains(e)).collect(Collectors.toList());
        }));
    }

    public List<T> getNoChangedValues() {
        return cache.computeIfAbsent("noChanged", key -> handle((org, cur) -> {
            Set<T> origin = new HashSet<>(org);
            return cur.stream().filter(origin::contains).collect(Collectors.toList());
        }));
    }

    protected List<T> handle(BiFunction<Collection<T>, Collection<T>, List<T>> biFunc) {
        Collection<T> org = Optional.ofNullable(original).orElse(Collections.emptyList());
        Collection<T> cur = Optional.ofNullable(current).orElse(Collections.emptyList());
        return biFunc.apply(org, cur);
    }
}
