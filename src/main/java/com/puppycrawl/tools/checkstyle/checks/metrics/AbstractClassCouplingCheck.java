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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.FileStatefulCheck;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FullIdent;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.CheckstyleCheckExecutionAdapter;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.domain.MetricSizeCheckService;
import com.puppycrawl.tools.checkstyle.utils.CommonUtil;
import com.puppycrawl.tools.checkstyle.utils.TokenUtil;

/**
 * Base class for coupling calculation.
 *
 */
@FileStatefulCheck
public abstract class AbstractClassCouplingCheck extends AbstractCheck {

    /** Hexagonal architecture entry point for metrics/size checks. */
    private final MetricSizeCheckService metricService =
            new MetricSizeCheckService(new CheckstyleCheckExecutionAdapter());

    /** A package separator - ".". */
    private static final char DOT = '.';

    /** Class names to ignore. */
    private static final Set<String> DEFAULT_EXCLUDED_CLASSES = Set.of(
        "var",
        "boolean", "byte", "char", "double", "float", "int",
        "long", "short", "void",
        "Boolean", "Byte", "Character", "Double", "Float",
        "Integer", "Long", "Short", "Void",
        "Object", "Class",
        "String", "StringBuffer", "StringBuilder",
        "ArrayIndexOutOfBoundsException", "Exception",
        "RuntimeException", "IllegalArgumentException",
        "IllegalStateException", "IndexOutOfBoundsException",
        "NullPointerException", "Throwable", "SecurityException",
        "UnsupportedOperationException",
        "List", "ArrayList", "Deque", "Queue", "LinkedList",
        "Set", "HashSet", "SortedSet", "TreeSet",
        "Map", "HashMap", "SortedMap", "TreeMap",
        "Override", "Deprecated", "SafeVarargs", "SuppressWarnings", "FunctionalInterface",
        "Collection", "EnumSet", "LinkedHashMap", "LinkedHashSet", "Optional",
        "OptionalDouble", "OptionalInt", "OptionalLong",
        "DoubleStream", "IntStream", "LongStream", "Stream"
    );

    /** Package names to ignore. */
    private static final Set<String> DEFAULT_EXCLUDED_PACKAGES = Collections.emptySet();

    /** Pattern to match brackets in a full type name. */
    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[[^]]*]");

    /** Specify user-configured regular expressions to ignore classes. */
    private final List<Pattern> excludeClassesRegexps = new ArrayList<>();

    /** A map of (imported class name -&gt; class name with package) pairs. */
    private final Map<String, String> importedClassPackages = new HashMap<>();

    /** Stack of class contexts. */
    private final Deque<ClassContext> classesContexts = new ArrayDeque<>();

    /** Specify user-configured class names to ignore. */
    private Set<String> excludedClasses = DEFAULT_EXCLUDED_CLASSES;

    /** Specify user-configured packages to ignore. */
    private Set<String> excludedPackages = DEFAULT_EXCLUDED_PACKAGES;

    /** Specify the maximum threshold allowed. */
    private int max;

    /** Current file package. */
    private String packageName;

    protected AbstractClassCouplingCheck(int defaultMax) {
        max = defaultMax;
        excludeClassesRegexps.add(CommonUtil.createPattern("^$"));
    }

    protected abstract String getLogMessageId();

    @Override
    public final int[] getDefaultTokens() {
        return getRequiredTokens();
    }

    public final void setMax(int max) {
        this.max = max;
    }

    public void setExcludedClasses(String... excludedClasses) {
        this.excludedClasses = Set.of(excludedClasses);
    }

    public void setExcludeClassesRegexps(Pattern... from) {
        excludeClassesRegexps.addAll(Arrays.asList(from));
    }

    public void setExcludedPackages(String... excludedPackages) {
        final List<String> invalidIdentifiers = Arrays.stream(excludedPackages)
            .filter(Predicate.not(CommonUtil::isName))
            .toList();
        if (!invalidIdentifiers.isEmpty()) {
            throw new IllegalArgumentException(
                "the following values are not valid identifiers: " + invalidIdentifiers);
        }

        this.excludedPackages = Set.of(excludedPackages);
    }

    @Override
    public final void beginTree(DetailAST ast) {
        metricService.executeMetricOrSizeCheck();
        importedClassPackages.clear();
        classesContexts.clear();
        classesContexts.push(new ClassContext("", null));
        packageName = "";
    }

    @Override
    public void visitToken(DetailAST ast) {
        switch (ast.getType()) {
            case TokenTypes.PACKAGE_DEF -> visitPackageDef(ast);
            case TokenTypes.IMPORT -> registerImport(ast);
            case TokenTypes.CLASS_DEF,
                 TokenTypes.INTERFACE_DEF,
                 TokenTypes.ANNOTATION_DEF,
                 TokenTypes.ENUM_DEF,
                 TokenTypes.RECORD_DEF -> visitClassDef(ast);
            case TokenTypes.EXTENDS_CLAUSE,
                 TokenTypes.IMPLEMENTS_CLAUSE,
                 TokenTypes.TYPE -> visitType(ast);
            case TokenTypes.LITERAL_NEW -> visitLiteralNew(ast);
            case TokenTypes.LITERAL_THROWS -> visitLiteralThrows(ast);
            case TokenTypes.ANNOTATION -> visitAnnotationType(ast);
            default -> throw new IllegalArgumentException("Unknown type: " + ast);
        }
    }

    @Override
    public void leaveToken(DetailAST ast) {
        if (TokenUtil.isTypeDeclaration(ast.getType())) {
            leaveClassDef();
        }
    }

    private void visitPackageDef(DetailAST pkg) {
        final FullIdent ident = FullIdent.createFullIdent(pkg.getLastChild().getPreviousSibling());
        packageName = ident.getText();
    }

    private void visitClassDef(DetailAST classDef) {
        final String className = classDef.findFirstToken(TokenTypes.IDENT).getText();
        createNewClassContext(className, classDef);
    }

    private void leaveClassDef() {
        checkCurrentClassAndRestorePrevious();
    }

    private void registerImport(DetailAST imp) {
        final FullIdent ident = FullIdent.createFullIdent(
            imp.getLastChild().getPreviousSibling());
        final String fullName = ident.getText();
        final int lastDot = fullName.lastIndexOf(DOT);
        importedClassPackages.put(fullName.substring(lastDot + 1), fullName);
    }

    private void createNewClassContext(String className, DetailAST ast) {
        classesContexts.push(new ClassContext(className, ast));
    }

    private void checkCurrentClassAndRestorePrevious() {
        classesContexts.pop().checkCoupling();
    }

    private void visitType(DetailAST ast) {
        classesContexts.peek().visitType(ast);
    }

    private void visitLiteralNew(DetailAST ast) {
        classesContexts.peek().visitLiteralNew(ast);
    }

    private void visitLiteralThrows(DetailAST ast) {
        classesContexts.peek().visitLiteralThrows(ast);
    }

    private void visitAnnotationType(DetailAST annotationAST) {
        final DetailAST children = annotationAST.getFirstChild();
        final DetailAST type = children.getNextSibling();
        classesContexts.peek().addReferencedClassName(type.getText());
    }

    private final class ClassContext {

        private final Set<String> referencedClassNames = new TreeSet<>();

        private final String className;

        private final DetailAST classAst;

        private ClassContext(String className, DetailAST ast) {
            this.className = className;
            classAst = ast;
        }

        /* package */ void visitLiteralThrows(DetailAST literalThrows) {
            for (DetailAST childAST = literalThrows.getFirstChild();
                 childAST != null;
                 childAST = childAST.getNextSibling()) {
                if (childAST.getType() != TokenTypes.COMMA) {
                    addReferencedClassName(childAST);
                }
            }
        }

        /* package */ void visitType(DetailAST ast) {
            DetailAST child = ast.getFirstChild();
            while (child != null) {
                if (TokenUtil.isOfType(child, TokenTypes.IDENT, TokenTypes.DOT)) {
                    final String fullTypeName = FullIdent.createFullIdent(child).getText();
                    final String trimmed = BRACKET_PATTERN
                            .matcher(fullTypeName).replaceAll("");
                    addReferencedClassName(trimmed);
                }
                child = child.getNextSibling();
            }
        }

        /* package */ void visitLiteralNew(DetailAST ast) {
            if (ast.getParent().getType() == TokenTypes.METHOD_REF) {
                addReferencedClassName(ast.getParent().getFirstChild());
            }
            else {
                addReferencedClassName(ast);
            }
        }

        private void addReferencedClassName(DetailAST ast) {
            final String fullIdentName = FullIdent.createFullIdent(ast).getText();
            final String trimmed = BRACKET_PATTERN
                    .matcher(fullIdentName).replaceAll("");
            addReferencedClassName(trimmed);
        }

        private void addReferencedClassName(String referencedClassName) {
            if (isSignificant(referencedClassName)) {
                referencedClassNames.add(referencedClassName);
            }
        }

        /* package */ void checkCoupling() {
            referencedClassNames.remove(className);
            referencedClassNames.remove(packageName + DOT + className);

            if (referencedClassNames.size() > max) {
                log(classAst, getLogMessageId(),
                        referencedClassNames.size(), max,
                        referencedClassNames.toString());
            }
        }

        private boolean isSignificant(String candidateClassName) {
            return !excludedClasses.contains(candidateClassName)
                && !isFromExcludedPackage(candidateClassName)
                && !isExcludedClassRegexp(candidateClassName);
        }

        private boolean isFromExcludedPackage(String candidateClassName) {
            String classNameWithPackage = candidateClassName;
            if (candidateClassName.indexOf(DOT) == -1) {
                classNameWithPackage = getClassNameWithPackage(candidateClassName)
                    .orElse("");
            }
            boolean isFromExcludedPackage = false;
            if (classNameWithPackage.indexOf(DOT) != -1) {
                final int lastDotIndex = classNameWithPackage.lastIndexOf(DOT);
                final String candidatePackageName =
                    classNameWithPackage.substring(0, lastDotIndex);
                isFromExcludedPackage = candidatePackageName.startsWith("java.lang")
                    || excludedPackages.contains(candidatePackageName);
            }
            return isFromExcludedPackage;
        }

        private Optional<String> getClassNameWithPackage(String examineClassName) {
            return Optional.ofNullable(importedClassPackages.get(examineClassName));
        }

        private boolean isExcludedClassRegexp(String candidateClassName) {
            boolean result = false;
            for (Pattern pattern : excludeClassesRegexps) {
                if (pattern.matcher(candidateClassName).matches()) {
                    result = true;
                    break;
                }
            }
            return result;
        }
    }
}