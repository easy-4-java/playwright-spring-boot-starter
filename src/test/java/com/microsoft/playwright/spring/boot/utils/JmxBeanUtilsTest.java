package com.microsoft.playwright.spring.boot.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JmxBeanUtils}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class JmxBeanUtilsTest {

    @Test
    void getObjectName_shouldCombinePackageNameAndType() {
        String name = JmxBeanUtils.getObjectName(JmxBeanUtilsTest.class);
        assertThat(name).isEqualTo(JmxBeanUtilsTest.class.getPackage().getName()
                + ":type=" + JmxBeanUtilsTest.class.getSimpleName());
    }

    @Test
    void getObjectName_forTopLevelCaller() {
        String name = JmxBeanUtils.getObjectName(String.class);
        assertThat(name).isEqualTo("java.lang:type=String");
    }

    @Test
    void getObjectName_forPrimitivesWrapper() {
        String name = JmxBeanUtils.getObjectName(Integer.class);
        assertThat(name).isEqualTo("java.lang:type=Integer");
    }

    @Test
    void defaultConstructor_isAccessibleViaReflection() throws Exception {
        java.lang.reflect.Constructor<JmxBeanUtils> ctor = JmxBeanUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
