package com.daiyc.codeless.fast.compare.processor;

import lombok.Data;
import lombok.experimental.Accessors;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author daiyc
 * @since 2024/10/6
 */
@Data
@Accessors(chain = true)
public class ComparisonMeta {
    public static final String GLOBAL_KEY = "";

    /**
     * 对比的类型
     */
    private DeclaredType type;

    /**
     * original 参数
     */
    private VariableElement originalParam;

    /**
     * current 参数
     */
    private VariableElement currentParam;

    /**
     * 字段比较配置
     */
    private Map<String, ComparingMeta> fieldComparingMeta;

    /**
     * 不需要比较的字段
     */
    private List<String> excludeFields;

    @Data
    @Accessors(chain = true)
    public static class ComparingMeta {
        /**
         * 当 original 的值为 null 时是否忽略
         */
        private boolean ignoreOriginalNull;

        /**
         * 当 current 的值为 null 时是否忽略
         */
        private boolean ignoreCurrentNull;
    }

    public String getOriginalName() {
        return originalParam.getSimpleName().toString();
    }

    public String getCurrentName() {
        return currentParam.getSimpleName().toString();
    }

    public boolean shouldIgnore(String field) {
        return excludeFields.contains(field) && !fieldComparingMeta.containsKey(field);
    }

    public boolean shouldIgnoreOriginalNull(String field) {
        return Optional.ofNullable(getComparingMeta(field)).map(ComparingMeta::isIgnoreOriginalNull).orElse(false);
    }

    public boolean shouldIgnoreCurrentNull(String field) {
        return Optional.ofNullable(getComparingMeta(field)).map(ComparingMeta::isIgnoreCurrentNull).orElse(false);
    }

    private ComparingMeta getComparingMeta(String field) {
        ComparingMeta comparingMeta = fieldComparingMeta.get(field);
        if (comparingMeta == null) {
            return fieldComparingMeta.get(GLOBAL_KEY);
        }
        return comparingMeta;
    }
}
