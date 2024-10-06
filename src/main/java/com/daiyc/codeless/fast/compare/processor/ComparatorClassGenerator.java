package com.daiyc.codeless.fast.compare.processor;

import com.daiyc.codeless.fast.compare.CollectionDiff;
import com.daiyc.codeless.fast.compare.Diffs;
import com.daiyc.codeless.fast.compare.ValueDiff;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.vavr.Tuple2;
import io.vavr.collection.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.daiyc.codeless.fast.compare.ComparatorConstants.IMPLEMENTATION_SUFFIX;

/**
 * @author daiyc
 * @since 2024/7/31
 */
@SuppressWarnings("unchecked")
class ComparatorClassGenerator {
    protected final ProcessingEnvironment processingEnv;

    protected final TypeElement interfaze;

    protected final Elements elementUtils;

    protected final Types typeUtils;

    protected final TypeElement objectTypeElement;

    private final TypeElement collectionType;

    protected final Map<Tuple2<String, String>, MethodSpec> helpMethods = new HashMap<>();

    protected TypeSpec cache = null;

    protected final TypeSpec.Builder classBuilder;

    ComparatorClassGenerator(ProcessingEnvironment processingEnv, TypeElement interfaze) {
        this.processingEnv = processingEnv;
        this.interfaze = interfaze;
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();

        this.objectTypeElement = elementUtils.getTypeElement("java.lang.Object");
        this.collectionType = elementUtils.getTypeElement("java.util.Collection");

        classBuilder = TypeSpec.classBuilder(interfaze.getSimpleName().toString() + IMPLEMENTATION_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(interfaze.asType());

        init();
    }

    protected void init() {
    }

    public TypeSpec generate() {
        if (cache != null) {
            return cache;
        }

        Stream.ofAll(getAllInterfaceMethods())
                .map(m -> this.generateMethodSpec(interfaze, m))
                .forEach(classBuilder::addMethod);

        helpMethods.values()
                .forEach(classBuilder::addMethod);

        return cache = classBuilder.build();
    }

    private MethodSpec generateMethodSpec(TypeElement interfaze, ExecutableElement method) {
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.size() != 2) {
            throw new IllegalArgumentException("参数个数必须为2");
        }
        MethodSpec.Builder methodBuilder = newMethodBuilder(interfaze, method);

        VariableElement var0 = parameters.get(0);
        VariableElement var1 = parameters.get(1);
        TypeMirror type0 = var0.asType();
        TypeMirror type1 = var1.asType();
        if (!typeUtils.isSameType(type0, type1)) {
            throw new IllegalArgumentException("参数的类型必须相同");
        }
        // TODO skip 基础类型

        DeclaredType declaredType = (DeclaredType) type0;
        TypeElement typeElement = (TypeElement) typeUtils.asElement(type0);
        ClassElementWrapper classElementWrapper = new ClassElementWrapper(elementUtils, typeUtils, typeElement);
        List<Tuple2<Element, ExecutableElement>> allProperties = classElementWrapper.getAllProperties();

        String originalName = var0.getSimpleName().toString();
        String currentName = var1.getSimpleName().toString();

        methodBuilder.addStatement("$T builder = $T.builder()", Diffs.Builder.class, Diffs.class);
        for (Tuple2<Element, ExecutableElement> property: allProperties) {
            TypeMirror propType = property._1.asType();
            String getterName = property._2.getSimpleName().toString();
            if (typeUtils.isAssignable(typeUtils.erasure(propType), typeUtils.erasure(collectionType.asType()))) {
                methodBuilder.beginControlFlow("if (!$T.equals($L.$L(), $L.$L()))", Objects.class, originalName, getterName, currentName, getterName);
                DeclaredType collType = (DeclaredType) propType;
                TypeMirror elementType = collType.getTypeArguments().get(0);
                methodBuilder.addStatement("builder.add($S, new $T($T.class, $T.class, $L.$L(), $L.$L()))"
                        , property._1.getSimpleName().toString()
                        , CollectionDiff.class, typeUtils.erasure(collType), elementType, originalName, getterName, currentName, getterName);
                methodBuilder.endControlFlow();
            } else {
                methodBuilder.beginControlFlow("if (!$T.equals($L.$L(), $L.$L()))", Objects.class, originalName, getterName, currentName, getterName);
                methodBuilder.addStatement("builder.add($S, new $T<>($T.class, $L.$L(), $L.$L()))"
                        , property._1.getSimpleName().toString()
                        , ValueDiff.class, propType, originalName, getterName, currentName, getterName);
                methodBuilder.endControlFlow();
            }
        }
        methodBuilder.addStatement("return builder.build()");
        return methodBuilder.build();
    }

    protected MethodSpec.Builder newMethodBuilder(TypeElement interfaze, ExecutableElement method) {
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

    protected TypeMirror findRealType(TypeElement interfaze, ExecutableElement method, TypeVariable typeVariable) {
        if (interfaze.equals(method.getEnclosingElement())) {
            throw new IllegalArgumentException("Comparator interface MUST NOT have any type variables");
        }
        return doFindRealType(interfaze.asType(), method, typeVariable);
    }

    /**
     * 获取所有需要实现的方法
     */
    protected List<ExecutableElement> getAllInterfaceMethods() {
        return ElementFilter.methodsIn(elementUtils.getAllMembers(interfaze))
                .stream()
                .filter(m -> !m.getEnclosingElement().equals(objectTypeElement))
                .filter(m -> !m.getModifiers().contains(Modifier.DEFAULT))
                .collect(Collectors.toList());
    }
}
