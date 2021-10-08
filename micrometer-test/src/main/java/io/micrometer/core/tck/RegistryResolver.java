/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.fail;

/**
 * @deprecated use {@link MeterRegistryCompatibilityKit#registry} instead.
 */
@Deprecated
public class RegistryResolver implements ParameterResolver {
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(MeterRegistry.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        @SuppressWarnings("ConstantConditions") Object o = extensionContext.getTestInstance().get();
        try {
            // support arbitrarily nested tests in the TCK
            Class<?> clazz = o.getClass();
            Object target = o;
            do {
                try {
                    Method registry = clazz.getMethod("registry");
                    registry.setAccessible(true); // because JUnit 5 test classes don't have to be public
                    return registry.invoke(target);
                } catch (NoSuchMethodException ignored) {
                }

                try {
                    target = o.getClass().getDeclaredField("this$0").get(target);
                } catch (NoSuchFieldException e) {
                    break;
                }
            } while ((clazz = clazz.getEnclosingClass()) != null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        fail("This should never happen -- an implementation of registry() was not found");
        return null;
    }
}
