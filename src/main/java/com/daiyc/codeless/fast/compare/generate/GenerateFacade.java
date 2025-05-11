package com.daiyc.codeless.fast.compare.generate;

import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * 生成门面
 *
 * @author daiyc
 * @since 2025/5/11
 */
public abstract class GenerateFacade {
    public static TypeSpec generate(ProcessingEnvironment processingEnv, TypeElement interfaze) {
        return new ComparatorClassGenerator(processingEnv, interfaze).generate();
    }
}
