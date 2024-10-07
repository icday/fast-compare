package com.daiyc.codeless.fast.compare.annotations;

import java.lang.annotation.*;

/**
 * @author daiyc
 * @since 2024/10/7
 */
@Documented
@Inherited
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface NotCompared {
    /**
     * 字段名
     */
    String[] value();
}
