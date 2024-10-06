package com.daiyc.codeless.fast.compare.processor;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author daiyc
 * @since 2024/7/30
 */
@SuppressWarnings("unchecked")
abstract class AnnotationUtils {

    public static Map<String, AnnotationValue> getAnnotationValues(Element param, TypeElement annElement) {
        AnnotationMirror annotationMirror = param.getAnnotationMirrors()
                .stream()
                .filter(ann -> ann.getAnnotationType().asElement().equals(annElement))
                .findFirst()
                .orElse(null);

        return getAnnotationValues(annotationMirror, annElement);
    }

    public static Map<String, AnnotationValue> getAnnotationValues(AnnotationMirror annotationMirror, TypeElement annElement) {
        Map<String, AnnotationValue> defaultValues = new HashMap<>();
        defaultValues = getDefaultValues(annElement);

        Map<String, AnnotationValue> annValues = new HashMap<>();
        if (annotationMirror != null) {
            annValues = io.vavr.collection.HashMap.ofAll(annotationMirror.getElementValues())
                    .mapKeys(k -> k.getSimpleName().toString())
                    .mapValues(v -> (AnnotationValue) v)
                    .toJavaMap();
        }

        defaultValues.putAll(annValues);

        return defaultValues;
    }

    private static Map<String, AnnotationValue> getDefaultValues(Element element) {
        return element.getEnclosedElements()
                .stream()
                .filter(el -> el.getKind() == ElementKind.METHOD)
                .map(el -> (ExecutableElement) el)
                .filter(el -> el.getDefaultValue() != null)
                .collect(Collectors.toMap(el -> el.getSimpleName().toString(), ExecutableElement::getDefaultValue));
    }

    public static ComparisonMeta parseComparison(TypeElement interfaze, ExecutableElement method, TypeElement annElement) {
        Map<String, AnnotationValue> comparesAnnValues = getAnnotationValues(method, annElement);

        ComparisonMeta meta = new ComparisonMeta();

        List<? extends VariableElement> parameters = method.getParameters();
        meta.setType((DeclaredType) parameters.get(0).asType());
        meta.setOriginalParam(parameters.get(0));
        meta.setCurrentParam(parameters.get(1));
        List<String> include = readStrings(comparesAnnValues.get("include"));
        List<String> exclude = readStrings(comparesAnnValues.get("exclude"));
        boolean ignoreOriginalNull = (boolean) comparesAnnValues.get("ignoreOriginalNull").getValue();
        boolean ignoreCurrentNull = (boolean) comparesAnnValues.get("ignoreCurrentNull").getValue();

        meta.setIncludeFields(include);
        meta.setExcludeFields(exclude);
        meta.setIgnoreOriginalNull(ignoreOriginalNull);
        meta.setIgnoreCurrentNull(ignoreCurrentNull);

        return meta;
    }

//    public static DeclaredType getEnumType(Map<String, AnnotationValue> annotationValueMap, String key) {
//        return Optional.ofNullable(annotationValueMap.get(key))
//                .map(v -> (DeclaredType) v.getValue())
//                .filter(c -> !"com.daiyc.extension.core.enums.None".equals(c.toString()))
//                .orElse(null);
//    }

    protected static List<String> readStrings(AnnotationValue annotationValue) {
        List<AnnotationValue> value = (List<AnnotationValue>) annotationValue.getValue();
        if (value == null) {
            return Collections.emptyList();
        }
        return value.stream()
                .map(i -> i.getValue().toString())
                .collect(Collectors.toList());
    }
}
