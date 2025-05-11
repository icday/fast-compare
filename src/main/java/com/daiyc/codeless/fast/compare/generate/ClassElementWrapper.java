package com.daiyc.codeless.fast.compare.generate;

import io.vavr.Tuple;
import io.vavr.Tuple3;
import lombok.Getter;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@Getter
class ClassElementWrapper {
    private final Elements elementUtils;

    private final Types typeUtils;

    private final TypeElement typeElement;

    private final List<Element> fields;

    private final List<ExecutableElement> methods;

    private final Map<String, List<ExecutableElement>> methodsByName;

    ClassElementWrapper(Elements elementUtils, Types typeUtils, TypeElement typeElement) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.typeElement = typeElement;

        List<? extends Element> allMembers = elementUtils.getAllMembers(typeElement);
        fields = allMembers.stream()
                .filter(m -> m.getKind().equals(ElementKind.FIELD))
                .collect(Collectors.toList());

        methods = allMembers.stream()
                .filter(m -> m.getKind().equals(ElementKind.METHOD))
                .map(m -> (ExecutableElement) m)
                .collect(Collectors.toList());

        methodsByName = methods.stream()
                .collect(Collectors.groupingBy(m -> m.getSimpleName().toString()));
    }

    public List<Tuple3<Element, ExecutableElement, ExecutableElement>> getAllProperties() {
        return fields.stream()
                .filter(f -> f.getModifiers().contains(Modifier.PRIVATE) || f.getModifiers().contains(Modifier.PROTECTED))
                .map(field -> Tuple.of(field, findGetterMethod(field), findSetterMethod(field)))
                .collect(Collectors.toList());
    }

    protected ExecutableElement findGetterMethod(Element field) {
        TypeMirror type = field.asType();
        String propName = field.getSimpleName().toString();
        String name = LOWER_CAMEL.to(UPPER_CAMEL, propName);
        List<String> guessNames = Arrays.asList("get" + name, "is" + name);
        return guessNames.stream()
                .flatMap(mn -> methodsByName.getOrDefault(mn, Collections.emptyList()).stream())
                .filter(m -> m.getModifiers().contains(Modifier.PUBLIC))
                .filter(method -> typeUtils.isSameType(method.getReturnType(), type) && method.getParameters().isEmpty())
                .findFirst()
                .orElse(null);
    }

    protected ExecutableElement findSetterMethod(Element field) {
        TypeMirror type = field.asType();
        String propName = field.getSimpleName().toString();
        String name = LOWER_CAMEL.to(UPPER_CAMEL, propName);
        List<String> guessNames = Collections.singletonList("set" + name);
        return guessNames.stream()
                .flatMap(mn -> methodsByName.getOrDefault(mn, Collections.emptyList()).stream())
                .filter(m -> m.getModifiers().contains(Modifier.PUBLIC))
                .filter(method -> method.getParameters().size() == 1 && typeUtils.isSameType(method.getParameters().get(0).asType(), type) )
                .findFirst()
                .orElse(null);
    }
}
