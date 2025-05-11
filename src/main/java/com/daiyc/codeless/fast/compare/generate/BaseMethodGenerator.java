package com.daiyc.codeless.fast.compare.generate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;

/**
 * @author daiyc
 * @since 2025/5/11
 */
abstract class BaseMethodGenerator implements MethodGenerator {
    protected final ComparatorClassGenerator classGenerator;

    protected final ProcessingEnvironment processingEnv;

    protected final TypeElement interfaze;

    protected final ExecutableElement method;

    protected final ClassElementWrapper classElementWrapper;

    protected final ComparisonMeta comparisonMeta;

    protected final Elements elementUtils;

    protected final Types typeUtils;

    protected final TypeElement objectType;

    BaseMethodGenerator(ComparatorClassGenerator classGenerator, ExecutableElement method, ComparisonMeta comparisonMeta) {
        this.classGenerator = classGenerator;
        this.processingEnv = classGenerator.processingEnv;
        this.interfaze = classGenerator.interfaze;
        this.method = method;
        this.comparisonMeta = comparisonMeta;
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();

        this.classElementWrapper = new ClassElementWrapper(elementUtils, typeUtils, (TypeElement) comparisonMeta.getType().asElement());

        this.objectType = elementUtils.getTypeElement(Object.class.getCanonicalName());
    }

    protected MethodSpec.Builder newMethodBuilder() {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(method.getReturnType()));

        List<? extends VariableElement> parameters = method.getParameters();

        for (VariableElement parameter : parameters) {
            TypeMirror parameterType = parameter.asType();
            if (parameterType instanceof TypeVariable) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, parameter + " is TypeVariable");
                TypeMirror resolvedType = findRealType(interfaze, method, (TypeVariable) parameterType);
                methodBuilder.addParameter(ClassName.get(resolvedType), parameter.getSimpleName().toString());
            } else {
                methodBuilder.addParameter(ClassName.get(parameterType), parameter.getSimpleName().toString());
            }
        }
        return methodBuilder;
    }

    protected TypeMirror findRealType(TypeElement interfaze, ExecutableElement method, TypeVariable typeVariable) {
        if (interfaze.equals(method.getEnclosingElement())) {
            throw new IllegalArgumentException("Comparator interface MUST NOT have any type variables");
        }
        return doFindRealType(interfaze.asType(), method, typeVariable);
    }

    protected TypeMirror doFindRealType(TypeMirror superInterface, ExecutableElement method, TypeVariable typeVariable) {
        DeclaredType declaredType = (DeclaredType) superInterface;

        Element enclosingElement = method.getEnclosingElement();
        // 方法定义的接口
        if (declaredType.asElement().equals(enclosingElement)) {
            return processingEnv.getTypeUtils().asMemberOf(declaredType, typeVariable.asElement());
        }

        List<? extends TypeMirror> parentInterfaces = ((TypeElement) ((DeclaredType) superInterface).asElement()).getInterfaces();
        if (parentInterfaces.isEmpty()) {
            return null;
        }

        for (TypeMirror parentInterface : parentInterfaces) {
            TypeMirror typeMirror = doFindRealType(parentInterface, method, typeVariable);
            if (typeMirror != null) {
                return typeMirror;
            }
        }
        return null;
    }
}
