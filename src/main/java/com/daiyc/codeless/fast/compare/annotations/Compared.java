package com.daiyc.codeless.fast.compare.annotations;

import java.lang.annotation.*;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Repeatable(ComparedList.class)
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE })
public @interface Compared {
    /**
     * 字段名，如果为空表示对所有字段。此时优先级最低
     */
    String[] field() default {};

    /**
     * 忽略 original 的空属性
     */
    boolean ignoreOriginalNull() default false;

    /**
     * 忽略 current 的空属性
     */
    boolean ignoreCurrentNull() default false;
}
