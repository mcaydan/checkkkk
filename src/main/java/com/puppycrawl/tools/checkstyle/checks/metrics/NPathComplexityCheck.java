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

package com.puppycrawl.tools.checkstyle.checks.metrics;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.out.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;
import com.puppycrawl.tools.checkstyle.utils.TokenUtil;

/**
 * Checks the NPATH complexity against a specified limit.
 *
 * @since 3.4
 */
// -@cs[AbbreviationAsWordInName] Can't change check name
@FileStatefulCheck
public final class NPathComplexityCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    public static final String MSG_KEY = "npathComplexity";

    private static final int[] CASE_LABEL_TOKENS = {
        TokenTypes.EXPR,
        TokenTypes.PATTERN_DEF,
        TokenTypes.PATTERN_VARIABLE_DEF,
        TokenTypes.RECORD_PATTERN_DEF,
    };

    private static final int DEFAULT_MAX = 200;

    private static final BigInteger INITIAL_VALUE = BigInteger.ZERO;

    private final Deque<BigInteger> rangeValues = new ArrayDeque<>();

    private final Deque<Integer> expressionValues = new ArrayDeque<>();

    private final Deque<Boolean> afterValues = new ArrayDeque<>();

    private final TokenEnd processingTokenEnd = new TokenEnd();

    private BigInteger currentRangeValue;

    private int max = DEFAULT_MAX;

    private boolean branchVisited;

    public void setMax(int max) {
        this.max = max;
    }

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {
            TokenTypes.CTOR_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.STATIC_INIT,
            TokenTypes.INSTANCE_INIT,
            TokenTypes.LITERAL_WHILE,
            TokenTypes.LITERAL_DO,
            TokenTypes.LITERAL_FOR,
            TokenTypes.LITERAL_IF,
            TokenTypes.LITERAL_ELSE,
            TokenTypes.LITERAL_SWITCH,
            TokenTypes.CASE_GROUP,
            TokenTypes.LITERAL_TRY,
            TokenTypes.LITERAL_CATCH,
            TokenTypes.QUESTION,
            TokenTypes.LITERAL_RETURN,
            TokenTypes.LITERAL_DEFAULT,
            TokenTypes.COMPACT_CTOR_DEF,
            TokenTypes.SWITCH_RULE,
            TokenTypes.LITERAL_WHEN,
        };
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        metricService.run();
        rangeValues.clear();
        expressionValues.clear();
        afterValues.clear();
        processingTokenEnd.reset();
        currentRangeValue = INITIAL_VALUE;
        branchVisited = false;
    }

    @Override
    public void visitToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.LITERAL_IF, TokenTypes.LITERAL_SWITCH,
                 TokenTypes.LITERAL_WHILE, TokenTypes.LITERAL_DO,
                 TokenTypes.LITERAL_FOR -> visitConditional(ast, 1);

            case TokenTypes.QUESTION -> visitUnitaryOperator(ast, 2);

            case TokenTypes.LITERAL_RETURN -> visitUnitaryOperator(ast, 0);

            case TokenTypes.LITERAL_WHEN -> visitWhenExpression(ast, 1);

            case TokenTypes.CASE_GROUP -> {
                final int caseNumber = countCaseTokens(ast);
                branchVisited = true;
                pushValue(caseNumber);
            }

            case TokenTypes.SWITCH_RULE -> {
                final int caseConstantNumber = countCaseConstants(ast);
                branchVisited = true;
                pushValue(caseConstantNumber);
            }

            case TokenTypes.LITERAL_ELSE -> {
                branchVisited = true;
                if (currentRangeValue.equals(BigInteger.ZERO)) {
                    currentRangeValue = BigInteger.ONE;
                }
                pushValue(0);
            }

            case TokenTypes.LITERAL_TRY,
                 TokenTypes.LITERAL_CATCH,
                 TokenTypes.LITERAL_DEFAULT -> pushValue(1);

            case TokenTypes.CTOR_DEF,
                 TokenTypes.METHOD_DEF,
                 TokenTypes.INSTANCE_INIT,
                 TokenTypes.STATIC_INIT,
                 TokenTypes.COMPACT_CTOR_DEF -> pushValue(0);

            default -> {
                // do nothing
            }
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.LITERAL_WHILE,
                 TokenTypes.LITERAL_DO,
                 TokenTypes.LITERAL_FOR,
                 TokenTypes.LITERAL_IF,
                 TokenTypes.LITERAL_SWITCH,
                 TokenTypes.LITERAL_WHEN -> leaveConditional();

            case TokenTypes.LITERAL_TRY -> leaveMultiplyingConditional();

            case TokenTypes.LITERAL_RETURN,
                 TokenTypes.QUESTION -> leaveUnitaryOperator();

            case TokenTypes.LITERAL_CATCH -> leaveAddingConditional();

            case TokenTypes.LITERAL_DEFAULT -> leaveBranch();

            case TokenTypes.LITERAL_ELSE,
                 TokenTypes.CASE_GROUP,
                 TokenTypes.SWITCH_RULE -> {
                leaveBranch();
                branchVisited = false;
            }

            case TokenTypes.CTOR_DEF,
                 TokenTypes.METHOD_DEF,
                 TokenTypes.INSTANCE_INIT,
                 TokenTypes.STATIC_INIT,
                 TokenTypes.COMPACT_CTOR_DEF -> leaveMethodDef(ast);

            default -> {
                // do nothing
            }
        }
    }

    private void visitConditional(DetailAST ast, int basicBranchingFactor) {
        int expressionValue = basicBranchingFactor;
        DetailAST bracketed;
        for (bracketed = ast.findFirstToken(TokenTypes.LPAREN);
                bracketed.getType() != TokenTypes.RPAREN;
                bracketed = bracketed.getNextSibling()) {
            expressionValue += countConditionalOperators(bracketed);
        }
        processingTokenEnd.setToken(bracketed);
        pushValue(expressionValue);
    }

    private void visitWhenExpression(DetailAST ast, int basicBranchingFactor) {
        final int expressionValue = basicBranchingFactor + countConditionalOperators(ast);
        processingTokenEnd.setToken(getLastToken(ast));
        pushValue(expressionValue);
    }

    private void visitUnitaryOperator(DetailAST ast, int basicBranchingFactor) {
        final boolean isAfter = processingTokenEnd.isAfter(ast);
        afterValues.push(isAfter);
        if (!isAfter) {
            processingTokenEnd.setToken(getLastToken(ast));
            final int expressionValue = basicBranchingFactor + countConditionalOperators(ast);
            pushValue(expressionValue);
        }
    }

    private void leaveUnitaryOperator() {
        if (Boolean.FALSE.equals(afterValues.pop())) {
            final Values valuePair = popValue();
            BigInteger basicRangeValue = valuePair.getRangeValue();
            BigInteger expressionValue = valuePair.getExpressionValue();
            if (expressionValue.equals(BigInteger.ZERO)) {
                expressionValue = BigInteger.ONE;
            }
            if (basicRangeValue.equals(BigInteger.ZERO)) {
                basicRangeValue = BigInteger.ONE;
            }
            currentRangeValue = currentRangeValue.add(expressionValue).multiply(basicRangeValue);
        }
    }

    private void leaveConditional() {
        final Values valuePair = popValue();
        final BigInteger expressionValue = valuePair.getExpressionValue();
        BigInteger basicRangeValue = valuePair.getRangeValue();
        if (currentRangeValue.equals(BigInteger.ZERO)) {
            currentRangeValue = BigInteger.ONE;
        }
        if (basicRangeValue.equals(BigInteger.ZERO)) {
            basicRangeValue = BigInteger.ONE;
        }
        currentRangeValue = currentRangeValue.add(expressionValue).multiply(basicRangeValue);
    }

    private void leaveBranch() {
        final Values valuePair = popValue();
        final BigInteger basicRangeValue = valuePair.getRangeValue();
        final BigInteger expressionValue = valuePair.getExpressionValue();
        if (branchVisited && currentRangeValue.equals(BigInteger.ZERO)) {
            currentRangeValue = BigInteger.ONE;
        }
        currentRangeValue = currentRangeValue.subtract(BigInteger.ONE)
                .add(basicRangeValue)
                .add(expressionValue);
    }

    private void leaveMethodDef(DetailAST ast) {
        final BigInteger bigIntegerMax = BigInteger.valueOf(max);
        if (currentRangeValue.compareTo(bigIntegerMax) > 0) {
            log(ast, MSG_KEY, currentRangeValue, bigIntegerMax);
        }
        popValue();
        currentRangeValue = INITIAL_VALUE;
    }

    private void leaveAddingConditional() {
        currentRangeValue = currentRangeValue.add(popValue().getRangeValue().add(BigInteger.ONE));
    }

    private void pushValue(Integer expressionValue) {
        rangeValues.push(currentRangeValue);
        expressionValues.push(expressionValue);
        currentRangeValue = INITIAL_VALUE;
    }

    private Values popValue() {
        final int expressionValue = expressionValues.pop();
        return new Values(rangeValues.pop(), BigInteger.valueOf(expressionValue));
    }

    private void leaveMultiplyingConditional() {
        currentRangeValue = currentRangeValue.add(BigInteger.ONE)
                .multiply(popValue().getRangeValue().add(BigInteger.ONE));
    }

    private static int countConditionalOperators(DetailAST ast) {
        int number = 0;
        for (DetailAST child = ast.getFirstChild(); child != null;
                child = child.getNextSibling()) {
            final int type = child.getType();
            if (type == TokenTypes.LOR || type == TokenTypes.LAND) {
                number++;
            }
            else if (type == TokenTypes.QUESTION) {
                number += 2;
            }
            number += countConditionalOperators(child);
        }
        return number;
    }

    private static DetailAST getLastToken(DetailAST ast) {
        final DetailAST lastChild = ast.getLastChild();
        final DetailAST result;
        if (lastChild.getFirstChild() == null) {
            result = lastChild;
        }
        else {
            result = getLastToken(lastChild);
        }
        return result;
    }

    private static int countCaseTokens(DetailAST ast) {
        int counter = 0;
        for (DetailAST iterator = ast.getFirstChild(); iterator != null;
                iterator = iterator.getNextSibling()) {
            if (iterator.getType() == TokenTypes.LITERAL_CASE) {
                counter++;
            }
        }
        return counter;
    }

    private static int countCaseConstants(DetailAST ast) {
        int counter = 0;
        final DetailAST literalCase = ast.getFirstChild();

        for (DetailAST node = literalCase.getFirstChild(); node != null;
                    node = node.getNextSibling()) {
            if (TokenUtil.isOfType(node, CASE_LABEL_TOKENS)) {
                counter++;
            }
        }

        return counter;
    }

    private static final class TokenEnd {

        private int endLineNo;

        private int endColumnNo;

        /* package */ void setToken(DetailAST endToken) {
            if (!isAfter(endToken)) {
                endLineNo = endToken.getLineNo();
                endColumnNo = endToken.getColumnNo();
            }
        }

        /* package */ void reset() {
            endLineNo = 0;
            endColumnNo = 0;
        }

        /* package */ boolean isAfter(DetailAST ast) {
            final int lineNo = ast.getLineNo();
            final int columnNo = ast.getColumnNo();
            return lineNo <= endLineNo
                && (lineNo != endLineNo
                || columnNo <= endColumnNo);
        }

    }

    private static final class Values {

        private final BigInteger rangeValue;

        private final BigInteger expressionValue;

        private Values(BigInteger valueOfRange, BigInteger valueOfExpression) {
            rangeValue = valueOfRange;
            expressionValue = valueOfExpression;
        }

        /* package */ BigInteger getRangeValue() {
            return rangeValue;
        }

        /* package */ BigInteger getExpressionValue() {
            return expressionValue;
        }

    }

}