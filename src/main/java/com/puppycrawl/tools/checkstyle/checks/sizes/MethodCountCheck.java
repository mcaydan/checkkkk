///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.puppycrawl.tools.checkstyle.checks.sizes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.Scope;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;
import com.puppycrawl.tools.checkstyle.utils.ScopeUtil;

/**
 * <div>
 * Checks the number of methods declared in each type declaration by access modifier
 * or total count.
 * </div>
 *
 * @since 5.3
 */
@FileStatefulCheck
public final class MethodCountCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    public static final String MSG_PRIVATE_METHODS = "too.many.privateMethods";

    public static final String MSG_PACKAGE_METHODS = "too.many.packageMethods";

    public static final String MSG_PROTECTED_METHODS = "too.many.protectedMethods";

    public static final String MSG_PUBLIC_METHODS = "too.many.publicMethods";

    public static final String MSG_MANY_METHODS = "too.many.methods";

    private static final int DEFAULT_MAX_METHODS = 100;

    private final Deque<MethodCounter> counters = new ArrayDeque<>();

    private int maxPrivate = DEFAULT_MAX_METHODS;

    private int maxPackage = DEFAULT_MAX_METHODS;

    private int maxProtected = DEFAULT_MAX_METHODS;

    private int maxPublic = DEFAULT_MAX_METHODS;

    private int maxTotal = DEFAULT_MAX_METHODS;

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.CLASS_DEF,
            TokenTypes.ENUM_CONSTANT_DEF,
            TokenTypes.ENUM_DEF,
            TokenTypes.INTERFACE_DEF,
            TokenTypes.ANNOTATION_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.RECORD_DEF,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {TokenTypes.METHOD_DEF};
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        metricService.executeMetricOrSizeCheck();
    }

    @Override
    public void visitToken(DetailAST ast) {
        if (ast.getType() == TokenTypes.METHOD_DEF) {
            if (isInLatestScopeDefinition(ast)) {
                raiseCounter(ast);
            }
        }
        else {
            counters.push(new MethodCounter(ast));
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        if (ast.getType() != TokenTypes.METHOD_DEF) {
            final MethodCounter counter = counters.pop();

            checkCounters(counter, ast);
        }
    }

    private boolean isInLatestScopeDefinition(DetailAST methodDef) {
        boolean result = false;

        if (!counters.isEmpty()) {
            final DetailAST latestDefinition = counters.peek().getScopeDefinition();
            result = latestDefinition == methodDef.getParent().getParent();
        }

        return result;
    }

    private void raiseCounter(DetailAST method) {
        final MethodCounter actualCounter = counters.peek();
        final Scope scope = ScopeUtil.getScope(method);
        actualCounter.increment(scope);
    }

    private void checkCounters(MethodCounter counter, DetailAST ast) {
        checkMax(maxPrivate, counter.value(Scope.PRIVATE), MSG_PRIVATE_METHODS, ast);
        checkMax(maxPackage, counter.value(Scope.PACKAGE), MSG_PACKAGE_METHODS, ast);
        checkMax(maxProtected, counter.value(Scope.PROTECTED), MSG_PROTECTED_METHODS, ast);
        checkMax(maxPublic, counter.value(Scope.PUBLIC), MSG_PUBLIC_METHODS, ast);
        checkMax(maxTotal, counter.getTotal(), MSG_MANY_METHODS, ast);
    }

    private void checkMax(int max, int value, String msg, DetailAST ast) {
        if (max < value) {
            log(ast, msg, value, max);
        }
    }

    public void setMaxPrivate(int value) {
        maxPrivate = value;
    }

    public void setMaxPackage(int value) {
        maxPackage = value;
    }

    public void setMaxProtected(int value) {
        maxProtected = value;
    }

    public void setMaxPublic(int value) {
        maxPublic = value;
    }

    public void setMaxTotal(int value) {
        maxTotal = value;
    }

    private static final class MethodCounter {

        private final Map<Scope, Integer> counts = new EnumMap<>(Scope.class);

        private final DetailAST scopeDefinition;

        private int total;

        private MethodCounter(DetailAST scopeDefinition) {
            this.scopeDefinition = scopeDefinition;
        }

        private void increment(Scope scope) {
            total++;
            counts.put(scope, 1 + value(scope));
        }

        private int value(Scope scope) {
            Integer value = counts.get(scope);
            if (value == null) {
                value = 0;
            }
            return value;
        }

        private DetailAST getScopeDefinition() {
            return scopeDefinition;
        }

        private int getTotal() {
            return total;
        }

    }

}