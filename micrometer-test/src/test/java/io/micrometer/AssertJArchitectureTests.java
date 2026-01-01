/*
 * Copyright 2026 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.assertj.core.annotation.CanIgnoreReturnValue;
import org.assertj.core.annotation.CheckReturnValue;
import org.assertj.core.api.Assert;

/**
 * Architecture tests for AssertJ.
 *
 * @author Stefano Cordio
 * @author Johnny Lim
 */
@AnalyzeClasses(packages = { "io.micrometer" })
class AssertJArchitectureTests {

    @ArchTest
    static final ArchRule allCustomAssertionMethodsNotReturningSelfShouldBeAnnotatedWithCheckReturnValue = ArchRuleDefinition
        .methods()
        .that()
        .areDeclaredInClassesThat()
        .implement(Assert.class)
        .and()
        .arePublic()
        .and()
        .doNotHaveRawReturnType(void.class)
        .and()
        .doNotHaveModifier(JavaModifier.BRIDGE)
        .and(doNotReturnSelfType())
        .and()
        .areNotAnnotatedWith(CanIgnoreReturnValue.class)
        .should()
        .beAnnotatedWith(CheckReturnValue.class)
        .allowEmptyShould(true);

    private static DescribedPredicate<JavaMethod> doNotReturnSelfType() {
        return DescribedPredicate.describe("do not return self type",
                (method) -> !method.getRawReturnType().equals(method.getOwner()));
    }

}
