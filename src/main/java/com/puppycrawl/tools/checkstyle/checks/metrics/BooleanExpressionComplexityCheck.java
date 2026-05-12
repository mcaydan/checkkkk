
package com.puppycrawl.tools.checkstyle.checks.metrics;

import java.util.ArrayDeque;
import java.util.Deque;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;
import com.puppycrawl.tools.checkstyle.utils.CheckUtil;


@FileStatefulCheck
public final class BooleanExpressionComplexityCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "booleanExpressionComplexity";

    /** Default allowed complexity. */
    private static final int DEFAULT_MAX = 3;

    /** Stack of contexts. */
    private final Deque<Context> contextStack = new ArrayDeque<>();
    /** Specify the maximum number of boolean operations allowed in one expression. */
    private int max;
    /** Current context. */
    private Context context = new Context(false);

    /** Creates new instance of the check. */
    public BooleanExpressionComplexityCheck() {
        max = DEFAULT_MAX;
    }

    @Override
    public int[] getDefaultTokens() {
        return new int[] {
            TokenTypes.CTOR_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.EXPR,
            TokenTypes.LAND,
            TokenTypes.BAND,
            TokenTypes.LOR,
            TokenTypes.BOR,
            TokenTypes.BXOR,
            TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {
            TokenTypes.CTOR_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.EXPR,
            TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.CTOR_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.EXPR,
            TokenTypes.LAND,
            TokenTypes.BAND,
            TokenTypes.LOR,
            TokenTypes.BOR,
            TokenTypes.BXOR,
            TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    /**
     * Setter to specify the maximum number of boolean operations allowed in one expression.
     *
     * @param max new maximum allowed complexity.
     * @since 3.4
     */
    public void setMax(int max) {
        this.max = max;
    }
    @Override
    public void beginTree(DetailAST rootAST) {
        metricService.executeMetricOrSizeCheck();
    }


    @Override
    public void visitToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.CTOR_DEF,
                 TokenTypes.METHOD_DEF,
                 TokenTypes.COMPACT_CTOR_DEF -> visitMethodDef(ast);

            case TokenTypes.EXPR -> visitExpr();

            case TokenTypes.BOR -> {
                if (!isPipeOperator(ast) && !isPassedInParameter(ast)) {
                    context.visitBooleanOperator();
                }
            }

            case TokenTypes.BAND,
                 TokenTypes.BXOR -> {
                if (!isPassedInParameter(ast)) {
                    context.visitBooleanOperator();
                }
            }

            case TokenTypes.LAND,
                 TokenTypes.LOR -> context.visitBooleanOperator();

            default -> throw new IllegalArgumentException("Unknown type: " + ast);
        }
    }

    /**
     * Checks if logical operator is part of constructor or method call.
     *
     * @param logicalOperator logical operator
     * @return true if logical operator is part of constructor or method call
     */
    private static boolean isPassedInParameter(DetailAST logicalOperator) {
        return logicalOperator.getParent().getParent().getType() == TokenTypes.ELIST;
    }

    /**
     * Checks if {@link TokenTypes#BOR binary OR} is applied to exceptions
     * in
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-14.html#jls-14.20">
     * multi-catch</a> (pipe-syntax).
     *
     * @param binaryOr {@link TokenTypes#BOR binary or}
     * @return true if binary or is applied to exceptions in multi-catch.
     */
    private static boolean isPipeOperator(DetailAST binaryOr) {
        return binaryOr.getParent().getType() == TokenTypes.TYPE;
    }

    @Override
    public void leaveToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.CTOR_DEF,
                 TokenTypes.METHOD_DEF,
                 TokenTypes.COMPACT_CTOR_DEF -> leaveMethodDef();

            case TokenTypes.EXPR -> leaveExpr(ast);

            default -> {
                // Do nothing
            }
        }
    }

    /**
     * Creates new context for a given method.
     *
     * @param ast a method we start to check.
     */
    private void visitMethodDef(DetailAST ast) {
        contextStack.push(context);
        final boolean check = !CheckUtil.isEqualsMethod(ast);
        context = new Context(check);
    }

    /** Removes old context. */
    private void leaveMethodDef() {
        context = contextStack.pop();
    }

    /** Creates and pushes new context. */
    private void visitExpr() {
        contextStack.push(context);
        context = new Context(context.isChecking());
    }

    /**
     * Restores previous context.
     *
     * @param ast expression we leave.
     */
    private void leaveExpr(DetailAST ast) {
        context.checkCount(ast);
        context = contextStack.pop();
    }

    /**
     * Represents context (method/expression) in which we check complexity.
     *
     */
    private final class Context {

        /**
         * Should we perform check in current context or not.
         * Usually false if we are inside equals() method.
         */
        private final boolean checking;
        /** Count of boolean operators. */
        private int count;

        /**
         * Creates new instance.
         *
         * @param checking should we check in current context or not.
         */
        private Context(boolean checking) {
            this.checking = checking;
        }

        /**
         * Getter for checking property.
         *
         * @return should we check in current context or not.
         */
        /* package */ boolean isChecking() {
            return checking;
        }

        /** Increases operator counter. */
        /* package */ void visitBooleanOperator() {
            ++count;
        }

        /**
         * Checks if we violate maximum allowed complexity.
         *
         * @param ast a node we check now.
         */
        /* package */ void checkCount(DetailAST ast) {
            if (checking && count > max) {
                final DetailAST parentAST = ast.getParent();

                log(parentAST, MSG_KEY, count, max);
            }
        }

    }

}

