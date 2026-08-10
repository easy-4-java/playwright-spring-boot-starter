/*
 * Copyright (c) 2018, Loong Wan (https://github.com/loong10k).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.microsoft.playwright.spring.boot.properties;

import org.springframework.boot.context.properties.PropertyMapper;
import com.microsoft.playwright.spring.boot.properties.PropertyMapperCompat;

/**
 * Compatibility layer for Spring Boot's {@link PropertyMapper}.
 *
 * <p>Spring Boot 4.x removed two convenience methods that this starter relied on:
 * {@code alwaysApplyingWhenNonNull()} (on the mapper) and
 * {@code Source} (on the source returned from {@code from()}).
 * This helper reproduces the original behaviour so that the starter's options
 * POJOs can keep using a familiar fluent API.</p>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * PropertyMapper map = PropertyMapperCompat.alwaysApplyingWhenNonNull();
 * PropertyMapper.Source<MyType> src = map.from(this.getFoo());
 * PropertyMapperCompat.whenNonNull(src).to(options::setFoo);
 * }</pre>
 *
 * @author <a href="https://github.com/loong10k">Loong Wan</a>
 * @since 4.0.0
 */
public final class PropertyMapperCompat {

    private PropertyMapperCompat() {
        // utility class
    }

    /**
     * Equivalent of the removed {@code PropertyMapper.alwaysApplyingWhenNonNull()}.
     * Returns a fresh {@link PropertyMapper} instance; the always-applying
     * semantics are baked into the {@link #whenNonNull(PropertyMapper.Source)}
     * wrapper that callers use on every chain.
     *
     * @return a new {@link PropertyMapper}
     */
    public static PropertyMapper alwaysApplyingWhenNonNull() {
        return PropertyMapper.get();
    }

    /**
     * Equivalent of the removed {@code Source} on
     * {@link PropertyMapper.Source}. Returns the original source filtered so
     * that {@code null} values are skipped.
     *
     * @param source the source to wrap
     * @param <T> the source type
     * @return a source that only passes non-null values to its consumer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> PropertyMapper.Source<T> whenNonNull(PropertyMapper.Source<T> source) {
        return (PropertyMapper.Source<T>) (PropertyMapper.Source) source.when(o -> o != null);
    }
}