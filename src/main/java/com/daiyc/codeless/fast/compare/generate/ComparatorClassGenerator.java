package com.daiyc.codeless.fast.compare.generate;

import com.daiyc.codeless.fast.compare.Diffs;
import com.daiyc.codeless.fast.compare.annotations.Compared;
import com.daiyc.codeless.fast.compare.annotations.ComparedList;
import com.daiyc.codeless.fast.compare.annotations.NotCompared;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.vavr.collection.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.stream.Collectors;

import static com.daiyc.codeless.fast.compare.ComparatorConstants.IMPLEMENTATION_SUFFIX;

/**
 * @author daiyc
 * @since 2024/7/31
 */
@SuppressWarnings("unchecked")
class ComparatorClassGenerator {
    final ProcessingEnvironment processingEnv;

    final TypeElement interfaze;

    final Elements elementUtils;

    final Types typeUtils;

    // region 各种需要的类型常量
    protected final TypeElement objectTypeElement;

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
                    .map(this::generateMethodSpec)
                    .forEach(classBuilder::addMethod);

            return cache = classBuilder.build();
        }
    }

    protected MethodGenerator newMethodGenerator(ExecutableElement method) {
        if (method.getParameters().size() != 2) {
            throw new IllegalArgumentException("参数个数必须为2");
        }

        TypeMirror type0 = method.getParameters().get(0).asType();
        TypeMirror type1 = method.getParameters().get(1).asType();
        if (!typeUtils.isSameType(type0, type1)) {
            throw new IllegalArgumentException("参数的类型必须相同");
        }

        TypeName type = ClassName.get(type0);
        if (type.isPrimitive() || type.isBoxedPrimitive()) {
            throw new IllegalArgumentException("基础类型不允许比较");
        }

        ComparisonMeta comparisonMeta = AnnotationUtils.parseComparison(interfaze, method
                , elementUtils.getTypeElement(ComparedList.class.getCanonicalName())
                , elementUtils.getTypeElement(Compared.class.getCanonicalName())
                , elementUtils.getTypeElement(NotCompared.class.getCanonicalName())
        );

        TypeMirror returnType = method.getReturnType();
        if (typeUtils.isSameType(returnType, diffsType.asType())) {
            return new DiffsMethodGenerator(this, method, comparisonMeta);
        } else if (typeUtils.isSameType(returnType, comparisonMeta.getType())) {
            return new SameTypeMethodGenerator(this, method, comparisonMeta);
        }
        throw new IllegalArgumentException("返回值类型错误");
    }

    private MethodSpec generateMethodSpec(ExecutableElement method) {
        MethodGenerator methodGenerator = newMethodGenerator(method);
        return methodGenerator.generate();
    }

    /**
     * 获取所有需要实现的方法
     */
    protected List<ExecutableElement> getAllInterfaceMethods() {
        return ElementFilter.methodsIn(elementUtils.getAllMembers(interfaze))
                .stream()
                // 忽略Object的方法
                .filter(m -> !m.getEnclosingElement().equals(objectTypeElement))
                .filter(m -> !m.getModifiers().contains(Modifier.DEFAULT))
                .collect(Collectors.toList());
    }

    List<ExecutableElement> getCustomCompareMethods() {
        return ElementFilter.methodsIn(elementUtils.getAllMembers(interfaze))
                .stream()
                // 忽略Object的方法
                .filter(m -> !m.getEnclosingElement().equals(objectTypeElement))
                .filter(m -> m.getModifiers().contains(Modifier.DEFAULT))
                .filter(m -> m.getParameters().size() == 2
                        && typeUtils.isSameType(m.getParameters().get(0).asType(), m.getParameters().get(1).asType())
                        // 返回值类型是布尔类型
                        && isBoolean(m.getReturnType()))
                .collect(Collectors.toList());
    }

    ExecutableElement getCustomCompareMethod(TypeMirror typeMirror) {
        return getCustomCompareMethods()
                .stream()
                .filter(m -> typeUtils.isSameType(m.getParameters().get(0).asType(), typeMirror))
                .findFirst()
                .orElse(null);
    }

    private boolean isBoolean(TypeMirror typeMirror) {
        if (typeMirror.getKind() == TypeKind.BOOLEAN) {
            return true;
        }

        TypeElement booleanTypeElement = elementUtils.getTypeElement("java.lang.Boolean");
        return typeUtils.isSameType(typeMirror, booleanTypeElement.asType());
    }
}
