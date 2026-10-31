package com.microsoft.playwright.spring.boot.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.PropertyMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PropertyMapperCompat}.
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 */
class PropertyMapperCompatTest {

    @Test
    void alwaysApplyingWhenNonNull_shouldReturnNonNullMapper() {
        PropertyMapper mapper = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        assertThat(mapper).isNotNull();
        // The same call returns fresh instances each time (PropertyMapper.get()).
        assertThat(PropertyMapperCompat.alwaysApplyingWhenNonNull()).isNotNull();
    }

    @Test
    void whenNonNull_shouldPassThroughNonNullValues() {
        PropertyMapper mapper = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        boolean[] touched = {false};
        PropertyMapperCompat.whenNonNull(mapper.from("value"))
                .to(v -> touched[0] = "value".equals(v));
        assertThat(touched[0]).isTrue();
    }

    @Test
    void whenNonNull_shouldSkipNullValues() {
        PropertyMapper mapper = PropertyMapperCompat.alwaysApplyingWhenNonNull();
        boolean[] touched = {false};
        PropertyMapperCompat.whenNonNull(mapper.from((String) null))
                .to(v -> touched[0] = true);
        assertThat(touched[0]).isFalse();
    }

    @Test
    void constructor_isPrivateAndInstantiableViaReflection() throws Exception {
        java.lang.reflect.Constructor<PropertyMapperCompat> ctor =
                PropertyMapperCompat.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
