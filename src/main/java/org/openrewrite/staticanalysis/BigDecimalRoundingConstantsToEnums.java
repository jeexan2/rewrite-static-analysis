/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.java.cleanup;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class BigDecimalRoundingConstantsToEnums extends Recipe {
    private static final MethodMatcher BIG_DECIMAL_DIVIDE = new MethodMatcher("java.math.BigDecimal divide(java.math.BigDecimal, int)");
    private static final MethodMatcher BIG_DECIMAL_DIVIDE_WITH_SCALE = new MethodMatcher("java.math.BigDecimal divide(java.math.BigDecimal, int, int)");
    private static final MethodMatcher BIG_DECIMAL_SET_SCALE = new MethodMatcher("java.math.BigDecimal setScale(int, int)");

    @Override
    public String getDisplayName() {
        return "`BigDecimal` rounding constants to `RoundingMode` enums";
    }

    @Override
    public String getDescription() {
        return "Convert `BigDecimal` rounding constants to the equivalent `RoundingMode` enum.";
    }

    @Override
    protected JavaVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("java.math.BigDecimal");
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final JavaTemplate twoArg = template("#{any(int)}, #{}")
                    .imports("java.math.RoundingMode").build();

            private final JavaTemplate threeArg = template("#{any(java.math.BigDecimal)}, #{any(int)}, #{}")
                    .imports("java.math.RoundingMode").build();

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);
                if ((BIG_DECIMAL_DIVIDE.matches(m) || BIG_DECIMAL_SET_SCALE.matches(m)) &&
                        isConvertibleBigDecimalConstant(m.getArguments().get(1))) {
                    m = m.withTemplate(twoArg, m.getCoordinates().replaceArguments(),
                            m.getArguments().get(0), getTemplateValue(m.getArguments().get(1)));
                    maybeAddImport("java.math.RoundingMode");
                } else if (BIG_DECIMAL_DIVIDE_WITH_SCALE.matches(m) &&
                        isConvertibleBigDecimalConstant(m.getArguments().get(2))) {
                    m = m.withTemplate(threeArg, m.getCoordinates().replaceArguments(),
                            m.getArguments().get(0), m.getArguments().get(1), getTemplateValue(m.getArguments().get(2)));
                    maybeAddImport("java.math.RoundingMode");
                }
                return m;
            }

            private boolean isConvertibleBigDecimalConstant(J elem) {
                if (elem instanceof J.Literal) {
                    return true;
                } else if (elem instanceof J.FieldAccess && ((J.FieldAccess) elem).getTarget().getType() instanceof JavaType.FullyQualified) {
                    J.FieldAccess fa = (J.FieldAccess) elem;
                    return fa.getTarget().getType() != null && TypeUtils.isOfClassType(fa.getTarget().getType(), "java.math.BigDecimal");
                }
                return false;
            }

            @Nullable
            private String getTemplateValue(J elem) {
                String roundingName = null;
                if (elem instanceof J.FieldAccess && ((J.FieldAccess) elem).getTarget().getType() instanceof JavaType.FullyQualified) {
                    J.FieldAccess fa = (J.FieldAccess) elem;
                    if (fa.getTarget().getType() != null && TypeUtils.isOfClassType(fa.getTarget().getType(), "java.math.BigDecimal")) {
                        roundingName = fa.getSimpleName();
                    }
                } else if (elem instanceof J.Literal) {
                    roundingName = ((J.Literal) elem).getValueSource();
                }
                if (roundingName != null) {
                    switch (roundingName) {
                        case "ROUND_UP":
                        case "0":
                            return "RoundingMode.UP";
                        case "ROUND_DOWN":
                        case "1":
                            return "RoundingMode.DOWN";
                        case "ROUND_CEILING":
                        case "2":
                            return "RoundingMode.CEILING";
                        case "ROUND_FLOOR":
                        case "3":
                            return "RoundingMode.FLOOR";
                        case "ROUND_HALF_UP":
                        case "4":
                            return "RoundingMode.HALF_UP";
                        case "ROUND_HALF_DOWN":
                        case "5":
                            return "RoundingMode.HALF_DOWN";
                        case "ROUND_HALF_EVEN":
                        case "6":
                            return "RoundingMode.HALF_EVEN";
                        case "ROUND_UNNECESSARY":
                        case "7":
                            return "RoundingMode.UNNECESSARY";
                    }
                }
                return null;
            }
        };
    }
}
