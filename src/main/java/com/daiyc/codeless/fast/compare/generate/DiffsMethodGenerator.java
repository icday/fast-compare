package com.daiyc.codeless.fast.compare.generate;

import com.daiyc.codeless.fast.compare.CollectionDiff;
import com.daiyc.codeless.fast.compare.Diffs;
import com.daiyc.codeless.fast.compare.ValueDiff;
import com.squareup.javapoet.MethodSpec;
import io.vavr.Tuple3;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author daiyc
 * @since 2025/5/11
 */
class DiffsMethodGenerator extends BaseMethodGenerator {
    final TypeElement collectionType;

    DiffsMethodGenerator(ComparatorClassGenerator classGenerator, ExecutableElement method, ComparisonMeta comparisonMeta) {
        super(classGenerator, method, comparisonMeta);
        this.collectionType = elementUtils.getTypeElement(Collection.class.getCanonicalName());
    }

    @Override
    public MethodSpec generate() {
        MethodSpec.Builder methodBuilder = newMethodBuilder();

        List<Tuple3<Element, ExecutableElement, ExecutableElement>> allProperties = classElementWrapper.getAllProperties();

        methodBuilder.addStatement("$T builder = $T.builder()", Diffs.Builder.class, Diffs.class);
        for (Tuple3<Element, ExecutableElement, ExecutableElement> property : allProperties) {
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

        ExecutableElement customCompareMethod = classGenerator.getCustomCompareMethod(propType);
        if (customCompareMethod == null) {
            blocks.add(() -> methodBuilder.beginControlFlow("if (!$T.equals($L.$L(), $L.$L()))"
                    , Objects.class, originalName, getterName, currentName, getterName));
        } else {
            blocks.add(() -> methodBuilder.beginControlFlow("if (!$L($L.$L(), $L.$L()))"
                    , customCompareMethod.getSimpleName().toString(), originalName, getterName, currentName, getterName));
        }

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
