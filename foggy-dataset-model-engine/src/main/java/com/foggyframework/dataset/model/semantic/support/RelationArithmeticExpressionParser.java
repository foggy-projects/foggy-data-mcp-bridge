package com.foggyframework.dataset.model.semantic.support;

/**
 * Parses the minimal governed arithmetic subset used by relation result-stage
 * derived metrics. This parser is intentionally package-private and narrower
 * than model-level calculated field expression parsing.
 */
final class RelationArithmeticExpressionParser {

    private final String input;
    private int pos;
    private boolean failed;

    private RelationArithmeticExpressionParser(String input) {
        this.input = input == null ? "" : input;
    }

    static RelationExpressionNode parse(String expr) {
        RelationArithmeticExpressionParser parser = new RelationArithmeticExpressionParser(expr);
        RelationExpressionNode node = parser.parseExpression();
        parser.skipWhitespace();
        if (parser.failed || node == null || parser.pos != parser.input.length()) {
            return null;
        }
        return node;
    }

    private RelationExpressionNode parseExpression() {
        return parseAdditive();
    }

    private RelationExpressionNode parseAdditive() {
        RelationExpressionNode node = parseMultiplicative();
        while (!failed) {
            skipWhitespace();
            if (peek('+') || peek('-')) {
                char op = input.charAt(pos++);
                RelationExpressionNode right = parseMultiplicative();
                if (right == null) {
                    return fail();
                }
                node = new RelationBinaryNode(String.valueOf(op), node, right);
            } else {
                return node;
            }
        }
        return null;
    }

    private RelationExpressionNode parseMultiplicative() {
        RelationExpressionNode node = parsePrimary();
        while (!failed) {
            skipWhitespace();
            if (peek('*') || peek('/')) {
                char op = input.charAt(pos++);
                RelationExpressionNode right = parsePrimary();
                if (right == null) {
                    return fail();
                }
                node = new RelationBinaryNode(String.valueOf(op), node, right);
            } else {
                return node;
            }
        }
        return null;
    }

    private RelationExpressionNode parsePrimary() {
        skipWhitespace();
        if (pos >= input.length()) {
            return fail();
        }
        if (peek('(')) {
            pos++;
            RelationExpressionNode node = parseExpression();
            skipWhitespace();
            if (!consume(')')) {
                return fail();
            }
            return node;
        }
        if (isNumberStart()) {
            return parseNumber();
        }
        if (isIdentifierStart(input.charAt(pos))) {
            String identifier = parseIdentifier();
            skipWhitespace();
            if (!peek('(')) {
                return new RelationAliasNode(identifier);
            }
            pos++;
            if ("ABS".equalsIgnoreCase(identifier)) {
                RelationExpressionNode child = parseExpression();
                skipWhitespace();
                if (!consume(')')) {
                    return fail();
                }
                return new RelationAbsNode(child);
            }
            if ("NULLIF".equalsIgnoreCase(identifier)) {
                RelationExpressionNode denominator = parsePrimary();
                if (!(denominator instanceof RelationAliasNode alias)) {
                    return fail();
                }
                skipWhitespace();
                if (!consume(',')) {
                    return fail();
                }
                RelationNumberNode zero = parseNumber();
                if (zero == null || !zero.isZero()) {
                    return fail();
                }
                skipWhitespace();
                if (!consume(')')) {
                    return fail();
                }
                return new RelationDenominatorGuardNode(alias.alias());
            }
            return fail();
        }
        return fail();
    }

    private RelationNumberNode parseNumber() {
        skipWhitespace();
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        boolean digits = false;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
            digits = true;
        }
        if (peek('.')) {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
                digits = true;
            }
        }
        if (!digits) {
            return fail();
        }
        return new RelationNumberNode(input.substring(start, pos));
    }

    private String parseIdentifier() {
        int start = pos;
        pos++;
        while (pos < input.length() && isIdentifierPart(input.charAt(pos))) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private boolean isNumberStart() {
        if (Character.isDigit(input.charAt(pos))) {
            return true;
        }
        return input.charAt(pos) == '-'
                && pos + 1 < input.length()
                && Character.isDigit(input.charAt(pos + 1));
    }

    private boolean consume(char expected) {
        skipWhitespace();
        if (!peek(expected)) {
            return false;
        }
        pos++;
        return true;
    }

    private boolean peek(char expected) {
        return pos < input.length() && input.charAt(pos) == expected;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RelationExpressionNode> T fail() {
        failed = true;
        return null;
    }

    private static boolean isIdentifierStart(char ch) {
        return ch == '_' || Character.isLetter(ch);
    }

    private static boolean isIdentifierPart(char ch) {
        return ch == '_' || ch == '$' || Character.isLetterOrDigit(ch);
    }

    interface RelationExpressionNode {
    }

    record RelationAliasNode(String alias) implements RelationExpressionNode {
    }

    record RelationNumberNode(String value) implements RelationExpressionNode {
        boolean isZero() {
            try {
                return Double.parseDouble(value) == 0.0d;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    record RelationBinaryNode(String op,
                              RelationExpressionNode left,
                              RelationExpressionNode right) implements RelationExpressionNode {
    }

    record RelationAbsNode(RelationExpressionNode child) implements RelationExpressionNode {
    }

    record RelationDenominatorGuardNode(String alias) implements RelationExpressionNode {
    }
}
