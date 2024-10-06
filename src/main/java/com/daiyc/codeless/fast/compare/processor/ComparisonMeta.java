package com.daiyc.codeless.fast.compare.processor;

import lombok.Data;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import java.util.List;

/**
 * @author daiyc
 * @since 2024/10/6
 */
@Data
public class ComparisonMeta {
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
     * 需要比较的字段
     */
    private List<String> includeFields;

    /**
     * 不需要比较的字段
     */
    private List<String> excludeFields;

    /**
     * 当 original 的值为 null 时是否忽略
     */
    private boolean ignoreOriginalNull;

    /**
     * 当 current 的值为 null 时是否忽略
     */
    private boolean ignoreCurrentNull;

    public String getOriginalName() {
        return originalParam.getSimpleName().toString();
    }

    public String getCurrentName() {
        return originalParam.getSimpleName().toString();
    }

    public boolean shouldIgnore(String field) {
        return excludeFields.contains(field) || includeFields.contains(field);
    }

    public boolean shouldIgnoreOriginalNull(String field) {
        return ignoreOriginalNull;
    }

    public boolean shouldIgnoreCurrentNull(String field) {
        return ignoreCurrentNull;
    }
}
