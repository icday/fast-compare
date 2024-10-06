package com.daiyc.codeless.fast.compare;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Data
public class Diffs {
    private final Map<String, Diff<?>> diffMap;

    private Diffs(Map<String, Diff<?>> diffMap) {
        this.diffMap = diffMap;
    }

    public boolean isEmpty() {
        return diffMap.isEmpty();
    }

    public Diffs ignore(String... fieldNames) {
        return ignore(Arrays.asList(fieldNames));
    }

    public Diffs ignore(Collection<String> fieldNames) {
        Collection<String> fs;
        if (fieldNames.size() > 8 && !(fieldNames instanceof Set)) {
            fs = new HashSet<>(fieldNames);
        } else {
            fs = fieldNames;
        }
        Map<String, Diff<?>> map = diffMap.entrySet().stream()
                .filter(e -> !fs.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new Diffs(map);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<Tuple2<String, Diff<?>>> diffs = new ArrayList<>();

        public Builder add(String field, Diff<?> diff) {
            this.diffs.add(Tuple.of(field, diff));
            return this;
        }

        public Diffs build() {
            Map<String, Diff<?>> map = diffs.stream()
                    .collect(Collectors.toMap(t -> t._1, t -> t._2));
            return new Diffs(map);
        }
    }
}
