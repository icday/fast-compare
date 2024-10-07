package com.daiyc.codeless.fast.compare.processor;

import io.vavr.Tuple;
import org.apache.commons.lang3.StringUtils;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.daiyc.codeless.fast.compare.processor.ComparisonMeta.GLOBAL_KEY;

/**
 * @author daiyc
 * @since 2024/7/30
 */
@SuppressWarnings("unchecked")
abstract class AnnotationUtils {

    public static Map<String, AnnotationValue> getAnnotationValues(Element annotatedElement, TypeElement annElement) {
        return findAllAnnotationValues(annotatedElement, annElement)
                .stream()
                .findFirst()
                .orElseGet(() -> getDefaultValues(annElement));
    }

    public static List<Map<String, AnnotationValue>> findAllAnnotationValues(Element annotatedElement, TypeElement annElement) {
        return annotatedElement.getAnnotationMirrors()
                .stream()
                .filter(ann -> ann.getAnnotationType().asElement().equals(annElement))
                .map(annotationMirror -> getAnnotationValues(annotationMirror, annElement))
                .collect(Collectors.toList());
    }

    public static Map<String, AnnotationValue> getAnnotationValues(AnnotationMirror annotationMirror, TypeElement annElement) {
        Map<String, AnnotationValue> defaultValues = getDefaultValues(annElement);

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

    public static Map<String, ComparisonMeta.ComparingMeta> parseComparisonMap(TypeElement interfaze, ExecutableElement method
            , TypeElement comparingListAnn, TypeElement comparingAnn) {

        AnnotationValue value = getAnnotationValues(method, comparingListAnn)
                .get("value");
        if (value == null) {
            return Collections.emptyMap();
        }

        return ((List<AnnotationValue>) value
                .getValue())
                .stream()
                .map(AnnotationValue::getValue)
                .map(v -> (AnnotationMirror) v)
                .map(am -> getAnnotationValues(am, comparingAnn))
                .flatMap(values -> {
                    List<String> fields = readStrings(values.get("field"));
                    ComparisonMeta.ComparingMeta comparingMeta = asComparingMeta(values);
                    if (fields.isEmpty()) {
                        return Stream.of(Tuple.of(GLOBAL_KEY, comparingMeta));
                    }
                    return fields.stream().map(field -> Tuple.of(field, comparingMeta));
                })
                .collect(Collectors.toMap(t -> t._1, t -> t._2, (a, b) -> a));
    }

    private static ComparisonMeta.ComparingMeta asComparingMeta(Map<String, AnnotationValue> comparesAnnValues) {
        return new ComparisonMeta.ComparingMeta()
                .setIgnoreOriginalNull((boolean) comparesAnnValues.get("ignoreOriginalNull").getValue())
                .setIgnoreCurrentNull((boolean) comparesAnnValues.get("ignoreCurrentNull").getValue());
    }

    public static List<String> parseNotComparing(TypeElement interfaze, ExecutableElement method, TypeElement annElement) {
        return findAllAnnotationValues(method, annElement)
                .stream()
                .flatMap(values -> readStrings(values.get("value")).stream())
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    public static ComparisonMeta parseComparison(TypeElement interfaze, ExecutableElement method
            , TypeElement comparingListAnn, TypeElement comparingAnn, TypeElement notComparingAnn) {
        List<String> notComparingFields = parseNotComparing(interfaze, method, notComparingAnn);

        Map<String, ComparisonMeta.ComparingMeta> comparingMap = parseComparisonMap(interfaze, method, comparingListAnn, comparingAnn);

        ComparisonMeta meta = new ComparisonMeta();

        List<? extends VariableElement> parameters = method.getParameters();
        meta.setType((DeclaredType) parameters.get(0).asType());
        meta.setOriginalParam(parameters.get(0));
        meta.setCurrentParam(parameters.get(1));
        meta.setFieldComparingMeta(comparingMap);
        meta.setExcludeFields(notComparingFields);

        return meta;
    }

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
