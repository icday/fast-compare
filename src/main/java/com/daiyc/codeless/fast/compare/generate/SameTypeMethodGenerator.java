package com.daiyc.codeless.fast.compare.generate;

import com.squareup.javapoet.MethodSpec;
import io.vavr.Tuple3;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author daiyc
 * @since 2025/5/11
 */
class SameTypeMethodGenerator extends BaseMethodGenerator {
    SameTypeMethodGenerator(ComparatorClassGenerator classGenerator, ExecutableElement method, ComparisonMeta comparisonMeta) {
        super(classGenerator, method, comparisonMeta);
    }

    @Override
    public MethodSpec generate() {
        MethodSpec.Builder methodBuilder = newMethodBuilder();

        TypeElement typeElement = (TypeElement) typeUtils.asElement(comparisonMeta.getType());
        ClassElementWrapper classElementWrapper = new ClassElementWrapper(elementUtils, typeUtils, typeElement);
        List<Tuple3<Element, ExecutableElement, ExecutableElement>> allProperties = classElementWrapper.getAllProperties();

        methodBuilder.addStatement("$T result = new $T()", typeElement, typeElement);

        for (Tuple3<Element, ExecutableElement, ExecutableElement> property : allProperties) {
            Element prop = property._1;
            String propName = prop.getSimpleName().toString();
            String getterName = property._2.getSimpleName().toString();
            String setterName = property._3.getSimpleName().toString();

            if (comparisonMeta.shouldIgnore(propName)) {
                methodBuilder.addComment("ignore property: " + propName);
                continue;
            }

            addPropertyToResult(methodBuilder, prop.asType(), getterName, setterName, propName);
        }

        methodBuilder.addStatement("return result");

        return methodBuilder.build();
    }

    private void addPropertyToResult(MethodSpec.Builder methodBuilder, TypeMirror propType,
                                     String getterName, String setterName, String propertyName) {
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

        blocks.add(() -> methodBuilder.addStatement("result.$L($L.$L())", setterName, currentName, getterName));
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
}
