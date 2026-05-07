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

import java.util.ArrayDeque;
import java.util.Deque;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.out.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;

/**
 * <div>
 * Determines complexity of methods, classes and files by counting
 * the Non Commenting Source Statements (NCSS). This check adheres to the
 * <a href="http://www.kclee.de/clemens/java/javancss/#specification">specification</a>
 * for the <a href="http://www.kclee.de/clemens/java/javancss/">JavaNCSS-Tool</a>
 * written by <b>Chr. Clemens Lee</b>.
 * </div>
 *
 * <p>
 * Roughly said the NCSS metric is calculated by counting the source lines which are
 * not comments, (nearly) equivalent to counting the semicolons and opening curly braces.
 * </p>
 *
 * <p>
 * The NCSS for a class is summarized from the NCSS of all its methods, the NCSS
 * of its nested classes and the number of member variable declarations.
 * </p>
 *
 * <p>
 * The NCSS for a file is summarized from the ncss of all its top level classes,
 * the number of imports and the package declaration.
 * </p>
 *
 * <p>
 * Rationale: Too large methods and classes are hard to read and costly to maintain.
 * A large NCSS number often means that a method or class has too many responsibilities
 * and/or functionalities which should be decomposed into smaller units.
 * </p>
 *
 * @since 3.5
 */
// -@cs[AbbreviationAsWordInName] We can not change it as,
// check's name is a part of API (used in configurations).
@FileStatefulCheck
public class JavaNCSSCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    public static final String MSG_METHOD = "ncss.method";

    public static final String MSG_CLASS = "ncss.class";

    public static final String MSG_RECORD = "ncss.record";

    public static final String MSG_FILE = "ncss.file";

    private static final int FILE_MAX_NCSS = 2000;

    private static final int CLASS_MAX_NCSS = 1500;

    private static final int RECORD_MAX_NCSS = 150;

    private static final int METHOD_MAX_NCSS = 50;

    private int fileMaximum = FILE_MAX_NCSS;

    private int classMaximum = CLASS_MAX_NCSS;

    private int recordMaximum = RECORD_MAX_NCSS;

    private int methodMaximum = METHOD_MAX_NCSS;

    private Deque<Counter> counters;

    @Override
    public int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {
            TokenTypes.CLASS_DEF,
            TokenTypes.INTERFACE_DEF,
            TokenTypes.METHOD_DEF,
            TokenTypes.CTOR_DEF,
            TokenTypes.INSTANCE_INIT,
            TokenTypes.STATIC_INIT,
            TokenTypes.PACKAGE_DEF,
            TokenTypes.IMPORT,
            TokenTypes.VARIABLE_DEF,
            TokenTypes.CTOR_CALL,
            TokenTypes.SUPER_CTOR_CALL,
            TokenTypes.LITERAL_IF,
            TokenTypes.LITERAL_ELSE,
            TokenTypes.LITERAL_WHILE,
            TokenTypes.LITERAL_DO,
            TokenTypes.LITERAL_FOR,
            TokenTypes.LITERAL_SWITCH,
            TokenTypes.LITERAL_BREAK,
            TokenTypes.LITERAL_CONTINUE,
            TokenTypes.LITERAL_RETURN,
            TokenTypes.LITERAL_THROW,
            TokenTypes.LITERAL_SYNCHRONIZED,
            TokenTypes.LITERAL_CATCH,
            TokenTypes.LITERAL_FINALLY,
            TokenTypes.EXPR,
            TokenTypes.LABELED_STAT,
            TokenTypes.LITERAL_CASE,
            TokenTypes.LITERAL_DEFAULT,
            TokenTypes.RECORD_DEF,
            TokenTypes.COMPACT_CTOR_DEF,
        };
    }

    @Override
    public int[] getAcceptableTokens() {
        return getRequiredTokens();
    }

    @Override
    public void beginTree(DetailAST rootAST) {
        metricService.run();
        counters = new ArrayDeque<>();
        counters.push(new Counter());
    }

    @Override
    public void visitToken(DetailAST ast) {
        final int tokenType = ast.getType();

        if (tokenType == TokenTypes.CLASS_DEF
            || tokenType == TokenTypes.RECORD_DEF
            || isMethodOrCtorOrInitDefinition(tokenType)) {
            counters.push(new Counter());
        }

        if (isCountable(ast)) {
            counters.forEach(Counter::increment);
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        final int tokenType = ast.getType();

        if (isMethodOrCtorOrInitDefinition(tokenType)) {
            final Counter counter = counters.pop();

            final int count = counter.getCount();
            if (count > methodMaximum) {
                log(ast, MSG_METHOD, count, methodMaximum);
            }
        }
        else if (tokenType == TokenTypes.CLASS_DEF) {
            final Counter counter = counters.pop();

            final int count = counter.getCount();
            if (count > classMaximum) {
                log(ast, MSG_CLASS, count, classMaximum);
            }
        }
        else if (tokenType == TokenTypes.RECORD_DEF) {
            final Counter counter = counters.pop();

            final int count = counter.getCount();
            if (count > recordMaximum) {
                log(ast, MSG_RECORD, count, recordMaximum);
            }
        }
    }

    @Override
    public void finishTree(DetailAST rootAST) {
        final Counter counter = counters.pop();

        final int count = counter.getCount();
        if (count > fileMaximum) {
            log(rootAST, MSG_FILE, count, fileMaximum);
        }
    }

    public void setFileMaximum(int fileMaximum) {
        this.fileMaximum = fileMaximum;
    }

    public void setClassMaximum(int classMaximum) {
        this.classMaximum = classMaximum;
    }

    public void setRecordMaximum(int recordMaximum) {
        this.recordMaximum = recordMaximum;
    }

    public void setMethodMaximum(int methodMaximum) {
        this.methodMaximum = methodMaximum;
    }

    private static boolean isCountable(DetailAST ast) {
        boolean countable = true;

        final int tokenType = ast.getType();

        if (tokenType == TokenTypes.EXPR) {
            countable = isExpressionCountable(ast);
        }
        else if (tokenType == TokenTypes.VARIABLE_DEF) {
            countable = isVariableDefCountable(ast);
        }
        return countable;
    }

    private static boolean isVariableDefCountable(DetailAST ast) {
        boolean countable = false;

        final int parentType = ast.getParent().getType();

        if (parentType == TokenTypes.SLIST
            || parentType == TokenTypes.OBJBLOCK) {
            final DetailAST prevSibling = ast.getPreviousSibling();

            countable = prevSibling == null
                    || prevSibling.getType() != TokenTypes.COMMA;
        }

        return countable;
    }

    private static boolean isExpressionCountable(DetailAST ast) {
        final int parentType = ast.getParent().getType();
        return switch (parentType) {
            case TokenTypes.SLIST, TokenTypes.LABELED_STAT, TokenTypes.LITERAL_FOR,
                 TokenTypes.LITERAL_DO,
                 TokenTypes.LITERAL_WHILE, TokenTypes.LITERAL_IF, TokenTypes.LITERAL_ELSE -> {
                final DetailAST prevSibling = ast.getPreviousSibling();
                yield prevSibling == null
                        || prevSibling.getType() != TokenTypes.LPAREN;
            }
            default -> false;
        };
    }

    private static boolean isMethodOrCtorOrInitDefinition(int tokenType) {
        return tokenType == TokenTypes.METHOD_DEF
                || tokenType == TokenTypes.COMPACT_CTOR_DEF
                || tokenType == TokenTypes.CTOR_DEF
                || tokenType == TokenTypes.STATIC_INIT
                || tokenType == TokenTypes.INSTANCE_INIT;
    }

    private static final class Counter {

        private int count;

        /* package */ void increment() {
            count++;
        }

        /* package */ int getCount() {
            return count;
        }

    }

}