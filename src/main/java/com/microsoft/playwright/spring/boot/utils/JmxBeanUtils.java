package com.microsoft.playwright.spring.boot.utils;

/**
 * Utility helpers for registering and naming JMX (Java Management Extensions) beans.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 1.0.0
 */
public class JmxBeanUtils {

    /**
     * Builds a JMX {@code ObjectName} string for the given bean class, using the package
     * as the domain and the simple class name as the type.
     * @param tClass the bean class to build an object name for
     * @param <T>    the bean type
     * @return a JMX object-name string of the form {@code <package>:type=<SimpleClassName>}
     */
    public static <T> String getObjectName(Class<T> tClass) {
        String packageName = tClass.getPackage().getName();
        return packageName + ":type=" + tClass.getSimpleName();
    }

}
