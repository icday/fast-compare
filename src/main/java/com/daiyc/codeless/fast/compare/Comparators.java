package com.daiyc.codeless.fast.compare;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static com.daiyc.codeless.fast.compare.ComparatorConstants.IMPLEMENTATION_SUFFIX;

/**
 * @author daiyc
 * @since 2024/10/4
 */
public class Comparators {

    public static <T> T getComparator(Class<T> clazz) {
        try {
            List<ClassLoader> classLoaders = collectClassLoaders( clazz.getClassLoader() );

            return getComparator( clazz, classLoaders );
        }
        catch ( ClassNotFoundException | NoSuchMethodException e ) {
            throw new RuntimeException( e );
        }
    }

    private static <T> T getComparator(Class<T> comparatorType, Iterable<ClassLoader> classLoaders)
            throws ClassNotFoundException, NoSuchMethodException {

        for ( ClassLoader classLoader : classLoaders ) {
            T comparator = doGetComparator( comparatorType, classLoader );
            if ( comparator != null ) {
                return comparator;
            }
        }

        throw new ClassNotFoundException("Cannot find implementation for " + comparatorType.getName() );
    }

    private static <T> T doGetComparator(Class<T> clazz, ClassLoader classLoader) throws NoSuchMethodException {
        try {
            @SuppressWarnings( "unchecked" )
            Class<T> implementation = (Class<T>) classLoader.loadClass( clazz.getName() + IMPLEMENTATION_SUFFIX );
            Constructor<T> constructor = implementation.getDeclaredConstructor();
            constructor.setAccessible( true );

            return constructor.newInstance();
        }
        catch (ClassNotFoundException e) {
            return getComparatorFromServiceLoader( clazz, classLoader );
        }
        catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException( e );
        }
    }

    public static <T> Class<? extends T> getComparatorClass(Class<T> clazz) {
        try {
            List<ClassLoader> classLoaders = collectClassLoaders( clazz.getClassLoader() );

            return getComparatorClass( clazz, classLoaders );
        }
        catch ( ClassNotFoundException e ) {
            throw new RuntimeException( e );
        }
    }

    private static <T> Class<? extends T> getComparatorClass(Class<T> comparatorType, Iterable<ClassLoader> classLoaders)
            throws ClassNotFoundException {

        for ( ClassLoader classLoader : classLoaders ) {
            Class<? extends T> comparatorClass = doGetComparatorClass( comparatorType, classLoader );
            if ( comparatorClass != null ) {
                return comparatorClass;
            }
        }

        throw new ClassNotFoundException( "Cannot find implementation for " + comparatorType.getName() );
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> doGetComparatorClass(Class<T> clazz, ClassLoader classLoader) {
        try {
            return (Class<? extends T>) classLoader.loadClass( clazz.getName() + IMPLEMENTATION_SUFFIX );
        }
        catch ( ClassNotFoundException e ) {
            T comparator = getComparatorFromServiceLoader( clazz, classLoader );
            if ( comparator != null ) {
                return (Class<? extends T>) comparator.getClass();
            }

            return null;
        }
    }

    private static <T> T getComparatorFromServiceLoader(Class<T> clazz, ClassLoader classLoader) {
        ServiceLoader<T> loader = ServiceLoader.load( clazz, classLoader );

        for ( T comparator : loader ) {
            if ( comparator != null ) {
                return comparator;
            }
        }

        return null;
    }

    private static List<ClassLoader> collectClassLoaders(ClassLoader classLoader) {
        List<ClassLoader> classLoaders = new ArrayList<>( 3 );
        classLoaders.add( classLoader );

        if ( Thread.currentThread().getContextClassLoader() != null ) {
            classLoaders.add( Thread.currentThread().getContextClassLoader() );
        }

        classLoaders.add( Comparators.class.getClassLoader() );

        return classLoaders;
    }
}
