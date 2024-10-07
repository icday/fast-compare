package com.daiyc.codeless.fast.compare.annotations;

import java.lang.annotation.*;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE })
public @interface ComparedList {
    Compared[] value();
}
