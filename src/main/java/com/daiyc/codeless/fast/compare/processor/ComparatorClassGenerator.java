package com.daiyc.codeless.fast.compare.processor;

import com.daiyc.codeless.fast.compare.CollectionDiff;
import com.daiyc.codeless.fast.compare.Diffs;
import com.daiyc.codeless.fast.compare.ValueDiff;
import com.daiyc.codeless.fast.compare.annotations.Compared;
import com.daiyc.codeless.fast.compare.annotations.ComparedList;
import com.daiyc.codeless.fast.compare.annotations.NotCompared;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.vavr.Tuple2;
import io.vavr.collection.Stream;
import lombok.RequiredArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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

    // region 各种需要的类型常量
    protected final TypeElement objectTypeElement;

    private final TypeElement collectionType;

    private final TypeElement diffsType;

    // endregion

    protected TypeSpec cache = null;

    protected final TypeSpec.Builder classBuilder;

    ComparatorClassGenerator(ProcessingEnvironment processingEnv, TypeElement interfaze) {
        this.processingEnv = processingEnv;
        this.interfaze = interfaze;
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();

        this.objectTypeElement = elementUtils.getTypeElement(Object.class.getCanonicalName());
        this.collectionType = elementUtils.getTypeElement(Collection.class.getCanonicalName());
        this.diffsType = elementUtils.getTypeElement(Diffs.class.getCanonicalName());

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

        synchronized (this) {
            if (cache != null) {
                return cache;
            }

            Stream.ofAll(getAllInterfaceMethods())
                    .map(m -> this.generateMethodSpec(interfaze, m))
                    .forEach(classBuilder::addMethod);

            return cache = classBuilder.build();
        }
    }

    private void validate(TypeElement interfaze, ExecutableElement method) {
        if (method.getParameters().size() != 2) {
            throw new IllegalArgumentException("参数个数必须为2");
        }

        TypeMirror type0 = method.getParameters().get(0).asType();
        TypeMirror type1 = method.getParameters().get(1).asType();
        if (!typeUtils.isSameType(type0, type1)) {
            throw new IllegalArgumentException("参数的类型必须相同");
        }

        TypeMirror returnType = method.getReturnType();
        if (!typeUtils.isSameType(returnType, diffsType.asType())) {
            throw new IllegalArgumentException("返回值必须是 Diffs 类型");
        }

        TypeName type = ClassName.get(type0);
        if (type.isPrimitive() || type.isBoxedPrimitive()) {
            throw new IllegalArgumentException("基础类型不允许比较");
        }
    }

    @RequiredArgsConstructor
    class MethodGenerator {
        private final ExecutableElement method;

        private final ComparisonMeta comparisonMeta;

        public MethodSpec generate() {
            MethodSpec.Builder methodBuilder = newMethodBuilder(interfaze, method);

            TypeElement typeElement = (TypeElement) typeUtils.asElement(comparisonMeta.getType());
            ClassElementWrapper classElementWrapper = new ClassElementWrapper(elementUtils, typeUtils, typeElement);
            List<Tuple2<Element, ExecutableElement>> allProperties = classElementWrapper.getAllProperties();

            methodBuilder.addStatement("$T builder = $T.builder()", Diffs.Builder.class, Diffs.class);
            for (Tuple2<Element, ExecutableElement> property : allProperties) {
                Element prop = property._1;
                String propName = prop.getSimpleName().toString();
                String getterName = property._2.getSimpleName().toString();

                if (comparisonMeta.shouldIgnore(propName)) {
                    methodBuilder.addComment("ignore property: " + propName);
                    continue;
                }

                addPropertyToBuilder(methodBuilder, prop.asType(), getterName, propName);
            }
            methodBuilder.addStatement("return builder.build()");
            return methodBuilder.build();
        }

        private void addPropertyToBuilder(MethodSpec.Builder methodBuilder, TypeMirror propType, String getterName, String propertyName) {
            String originalName = comparisonMeta.getOriginalName();
            String currentName = comparisonMeta.getCurrentName();

            LinkedList<Runnable> blocks = new LinkedList<>();
            Runnable nestedBlock;
            if (isCollectionType(propType)) {
                DeclaredType collType = (DeclaredType) propType;
                TypeMirror elementType = collType.getTypeArguments().get(0);
                nestedBlock = () -> methodBuilder.addStatement("builder.add($S, new $T<>($T.class, $T.class, $L.$L(), $L.$L()))"
                        , propertyName, CollectionDiff.class, typeUtils.erasure(propType), elementType, originalName, getterName, currentName, getterName);
            } else {
                nestedBlock = () -> methodBuilder.addStatement("builder.add($S, new $T<>($T.class, $L.$L(), $L.$L()))"
                        , propertyName, ValueDiff.class, typeUtils.erasure(propType), originalName, getterName, currentName, getterName);
            }

            blocks.add(() -> methodBuilder.beginControlFlow("if (!$T.equals($L.$L(), $L.$L()))"
                    , Objects.class, originalName, getterName, currentName, getterName));
            blocks.add(nestedBlock);
            blocks.add(methodBuilder::endControlFlow);

            if (comparisonMeta.shouldIgnoreOriginalNull(propertyName)) {
                blocks.addFirst(() -> methodBuilder.beginControlFlow("if ($L.$L() != null)", originalName, getterName));
                blocks.addLast(methodBuilder::endControlFlow);
            }

            if (comparisonMeta.shouldIgnoreCurrentNull(propertyName)) {
                blocks.addFirst(() -> methodBuilder.beginControlFlow("if ($L.$L() != null)", currentName, getterName));
                blocks.addLast(methodBuilder::endControlFlow);
            }

            blocks.forEach(Runnable::run);
        }

        private boolean isCollectionType(TypeMirror propType) {
            return typeUtils.isAssignable(typeUtils.erasure(propType), typeUtils.erasure(collectionType.asType()));
        }
    }

    private MethodSpec generateMethodSpec(TypeElement interfaze, ExecutableElement method) {
        validate(interfaze, method);
        ComparisonMeta comparisonMeta = AnnotationUtils.parseComparison(interfaze, method
                , elementUtils.getTypeElement(ComparedList.class.getCanonicalName())
                , elementUtils.getTypeElement(Compared.class.getCanonicalName())
                , elementUtils.getTypeElement(NotCompared.class.getCanonicalName())
        );
        return new MethodGenerator(method, comparisonMeta).generate();
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
