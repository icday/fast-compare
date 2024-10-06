package com.daiyc.codeless.fast.compare.processor;

import com.daiyc.codeless.fast.compare.ComparatorConstants;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import lombok.SneakyThrows;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * @author daiyc
 * @since 2024/10/4
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes(ComparatorConstants.COMPARATOR)
public class ComparatorProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            String annName = annotation.getQualifiedName().toString();
            boolean ok = true;
            if (annName.equals(ComparatorConstants.COMPARATOR)) {
                ok = handleComparator(annotation, roundEnv);
            }

            if (!ok) {
                return false;
            }
        }
        return true;
    }

    protected boolean handleComparator(TypeElement annotation, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
            if (!isTypeElement(element)) {
                continue;
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "found @Comparator at " + element);
            if (!processComparator(element)) {
                return false;
            }
        }
        return true;
    }

    @SneakyThrows
    protected boolean processComparator(Element element) {
        if (!isTypeElement(element)) {
            return true;
        }

        TypeElement typeElement = (TypeElement) element;

        JavaFile javaFile = generateClass(typeElement);
        javaFile.writeTo(processingEnv.getFiler());
        return true;
    }

    protected JavaFile generateClass(TypeElement interfaze) {
        Elements elementUtils = processingEnv.getElementUtils();
        String packageName = elementUtils.getPackageOf(interfaze).getQualifiedName().toString();

        ComparatorClassGenerator comparatorClassGenerator = new ComparatorClassGenerator(processingEnv, interfaze);

        return JavaFile.builder(packageName, comparatorClassGenerator.generate())
                .build();
    }

    private boolean isTypeElement(Element element) {
        return element instanceof TypeElement;
    }
}
