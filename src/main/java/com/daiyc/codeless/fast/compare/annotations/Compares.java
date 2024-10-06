package com.daiyc.codeless.fast.compare.annotations;

import java.lang.annotation.*;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Documented
@Inherited
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Compares {
    /**
     * 需要比较的字段
     */
    String[] include() default {};

    /**
     * 忽略比较的字段
     */
    String[] exclude() default {};

    /**
     * 忽略 original 的空属性
     */
    boolean ignoreOriginalNull() default false;

    /**
     * 忽略 current 的空属性
     */
    boolean ignoreCurrentNull() default false;
}
