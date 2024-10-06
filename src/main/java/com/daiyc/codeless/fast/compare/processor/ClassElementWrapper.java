package com.daiyc.codeless.fast.compare.processor;

import io.vavr.Tuple;
import io.vavr.Tuple2;

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
class ClassElementWrapper {
    private final Elements elementUtils;

    private final Types typeUtils;

    private final TypeElement typeElement;

    private final List<Element> fields;

    private final List<ExecutableElement> methods;

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
    }

    public List<Tuple2<Element, ExecutableElement>> getAllProperties() {
        Map<String, List<ExecutableElement>> methodsByName = methods.stream()
                .collect(Collectors.groupingBy(m -> m.getSimpleName().toString()));

        return fields.stream()
                .filter(f -> f.getModifiers().contains(Modifier.PRIVATE) || f.getModifiers().contains(Modifier.PROTECTED))
                .map(field -> {
                    TypeMirror type = field.asType();
                    String fieldName = field.getSimpleName().toString();
                    String name = LOWER_CAMEL.to(UPPER_CAMEL, fieldName);
                    List<String> guessNames = Arrays.asList("get" + name, "is" + name);
                    return guessNames.stream()
                            .flatMap(mn -> methodsByName.getOrDefault(mn, Collections.emptyList()).stream())
                            .filter(m -> m.getModifiers().contains(Modifier.PUBLIC))
                            .filter(method -> typeUtils.isSameType(method.getReturnType(), type) && method.getParameters().isEmpty())
                            .findFirst()
                            .map(g -> Tuple.of(field, g))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
