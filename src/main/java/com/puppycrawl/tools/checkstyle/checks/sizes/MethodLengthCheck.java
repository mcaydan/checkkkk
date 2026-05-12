

package com.puppycrawl.tools.checkstyle.checks.sizes;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Stream;

import com.puppycrawl.tools.checkstyle.StatelessCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;


@StatelessCheck
public class MethodLengthCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    /**
     * A key is pointing to the warning message text in "messages.properties"
     * file.
     */
    public static final String MSG_KEY = "maxLen.method";

    /** Default maximum number of lines. */
    private static final int DEFAULT_MAX_LINES = 150;

    /** Control whether to count empty lines and comments. */
    private boolean countEmpty = true;

    /** Specify the maximum number of lines allowed. */
    private int max = DEFAULT_MAX_LINES;

    @Override
    public int[] getDefaultTokens() {
        return getAcceptableTokens();
    }

    @Override
    public int[] getAcceptableTokens() {
        return new int[] {
            TokenTypes.METHOD_DEF,
            TokenTypes.CTOR_DEF,
            TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    @Override
    public int[] getRequiredTokens() {
        return CommonUtil.EMPTY_INT_ARRAY;
    }
    @Override
    public void beginTree(DetailAST rootAST) {
        metricService.executeMetricOrSizeCheck();
    }


    @Override
    public void visitToken(DetailAST ast) {
        final DetailAST openingBrace = ast.findFirstToken(TokenTypes.SLIST);
        if (openingBrace != null) {
            final int length;
            if (countEmpty) {
                final DetailAST closingBrace = openingBrace.findFirstToken(TokenTypes.RCURLY);
                length = getLengthOfBlock(openingBrace, closingBrace);
            }
            else {
                length = countUsedLines(openingBrace);
            }
            if (length > max) {
                final String methodName = ast.findFirstToken(TokenTypes.IDENT).getText();
                log(ast, MSG_KEY, length, max, methodName);
            }
        }
    }

    /**
     * Returns length of code.
     *
     * @param openingBrace block opening brace
     * @param closingBrace block closing brace
     * @return number of lines with code for current block
     */
    private static int getLengthOfBlock(DetailAST openingBrace, DetailAST closingBrace) {
        final int startLineNo = openingBrace.getLineNo();
        final int endLineNo = closingBrace.getLineNo();
        return endLineNo - startLineNo + 1;
    }

    /**
     * Count number of used code lines without comments.
     *
     * @param ast start ast
     * @return number of used lines of code
     */
    private static int countUsedLines(DetailAST ast) {
        final Deque<DetailAST> nodes = new ArrayDeque<>();
        nodes.add(ast);
        final BitSet usedLines = new BitSet();
        while (!nodes.isEmpty()) {
            final DetailAST node = nodes.removeFirst();
            final int lineIndex = node.getLineNo();
            // text block requires special treatment,
            // since it is the only non-comment token that can span more than one line
            if (node.getType() == TokenTypes.TEXT_BLOCK_LITERAL_BEGIN) {
                final int endLineIndex = node.getLastChild().getLineNo();
                usedLines.set(lineIndex, endLineIndex + 1);
            }
            else {
                usedLines.set(lineIndex);
                Stream.iterate(
                    node.getLastChild(), Objects::nonNull, DetailAST::getPreviousSibling
                ).forEach(nodes::addFirst);
            }
        }
        return usedLines.cardinality();
    }

    /**
     * Setter to specify the maximum number of lines allowed.
     *
     * @param length the maximum length of a method.
     * @since 3.0
     */
    public void setMax(int length) {
        max = length;
    }

    /**
     * Setter to control whether to count empty lines and comments.
     *
     * @param countEmpty whether to count empty and comments.
     * @since 3.2
     */
    public void setCountEmpty(boolean countEmpty) {
        this.countEmpty = countEmpty;
    }

}

