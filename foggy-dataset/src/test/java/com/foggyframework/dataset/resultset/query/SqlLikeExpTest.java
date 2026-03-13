package com.foggyframework.dataset.resultset.query;

import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.support.ArrayRecord;
import com.foggyframework.dataset.resultset.support.ListResultSetMetaDataSupport;
import com.foggyframework.dataset.resultset.support.ListResultSetSupport;
import com.foggyframework.dataset.resultset.query.ResultSetQueryImpl.SqlLikeExp;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SqlLikeExp} - SQL LIKE pattern matching on in-memory result sets.
 */
public class SqlLikeExpTest {

    // ========== likeToRegex conversion tests ==========

    @Test
    public void testLikeToRegex_percentWildcard() {
        // % should map to .*
        String regex = SqlLikeExp.likeToRegex("%value%");
        assertEquals("^.*value.*$", regex);
    }

    @Test
    public void testLikeToRegex_underscoreWildcard() {
        // _ should map to .
        String regex = SqlLikeExp.likeToRegex("a_c");
        assertEquals("^a.c$", regex);
    }

    @Test
    public void testLikeToRegex_noWildcards() {
        // no wildcards = exact match
        String regex = SqlLikeExp.likeToRegex("hello");
        assertEquals("^hello$", regex);
    }

    @Test
    public void testLikeToRegex_emptyPattern() {
        String regex = SqlLikeExp.likeToRegex("");
        assertEquals("^$", regex);
    }

    @Test
    public void testLikeToRegex_escapesRegexSpecialChars() {
        // Dot, plus, star (as literal), caret, dollar, brackets, parens, braces, pipe, question mark
        String regex = SqlLikeExp.likeToRegex("a.b+c^d$e[f]g(h)i{j}k|l?m");
        assertEquals("^a\\.b\\+c\\^d\\$e\\[f\\]g\\(h\\)i\\{j\\}k\\|l\\?m$", regex);
    }

    @Test
    public void testLikeToRegex_backslashEscaped() {
        String regex = SqlLikeExp.likeToRegex("a\\b");
        assertEquals("^a\\\\b$", regex);
    }

    @Test
    public void testLikeToRegex_mixedWildcardsAndSpecialChars() {
        // %test.value% should match any string containing "test.value"
        String regex = SqlLikeExp.likeToRegex("%test.value%");
        assertEquals("^.*test\\.value.*$", regex);
    }

    @Test
    public void testLikeToRegex_prefixPattern() {
        String regex = SqlLikeExp.likeToRegex("value%");
        assertEquals("^value.*$", regex);
    }

    @Test
    public void testLikeToRegex_suffixPattern() {
        String regex = SqlLikeExp.likeToRegex("%value");
        assertEquals("^.*value$", regex);
    }

    @Test
    public void testLikeToRegex_onlyPercent() {
        String regex = SqlLikeExp.likeToRegex("%");
        assertEquals("^.*$", regex);
    }

    @Test
    public void testLikeToRegex_onlyUnderscore() {
        String regex = SqlLikeExp.likeToRegex("_");
        assertEquals("^.$", regex);
    }

    @Test
    public void testLikeToRegex_multipleUnderscores() {
        String regex = SqlLikeExp.likeToRegex("___");
        assertEquals("^...$", regex);
    }

    @Test
    public void testLikeToRegex_percentAndUnderscoreMixed() {
        String regex = SqlLikeExp.likeToRegex("%a_b%");
        assertEquals("^.*a.b.*$", regex);
    }

    // ========== evalValue integration tests ==========

    /**
     * Helper: evaluate LIKE match by creating the minimal infrastructure.
     * Returns true if {@code actualValue LIKE likePattern}.
     */
    private boolean evalLike(Object actualValue, String likePattern) {
        // Create a single-column metadata and record
        ListResultSetMetaDataSupport<Object> meta =
                new ListResultSetMetaDataSupport<>(Arrays.asList("COL"));
        ArrayRecord<Object> record;
        try {
            record = (ArrayRecord<Object>) meta.newRecord(0);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        record.values[0] = actualValue;

        // SelectColumn with columnIndex=0 maps to getObject(1) -> values[0]
        SelectColumn sc = new SelectColumn("COL", 0, null);

        // Create SqlLikeExp
        SqlLikeExp likeExp = new SqlLikeExp(sc, likePattern);

        // Create WhereExpEvaluator via ResultSetQueryImpl
        ListResultSetSupport<Object> rs =
                new ListResultSetSupport<>(meta, Collections.emptyList());
        ResultSetQueryImpl query = new ResultSetQueryImpl(rs);
        ResultSetQueryImpl.WhereExpEvaluator evaluator = query.new WhereExpEvaluator();
        evaluator.record = record;

        return (Boolean) likeExp.evalValue(evaluator);
    }

    // --- Substring matching: %value% ---

    @Test
    public void testEvalValue_substringMatch() {
        assertTrue(evalLike("hello world", "%world%"));
        assertTrue(evalLike("hello world", "%hello%"));
        assertTrue(evalLike("hello world", "%lo wo%"));
        assertTrue(evalLike("hello world", "%hello world%"));
    }

    @Test
    public void testEvalValue_substringNoMatch() {
        assertFalse(evalLike("hello world", "%xyz%"));
    }

    // --- Prefix matching: value% ---

    @Test
    public void testEvalValue_prefixMatch() {
        assertTrue(evalLike("hello world", "hello%"));
        assertTrue(evalLike("hello", "hello%"));
    }

    @Test
    public void testEvalValue_prefixNoMatch() {
        assertFalse(evalLike("hello world", "world%"));
    }

    // --- Suffix matching: %value ---

    @Test
    public void testEvalValue_suffixMatch() {
        assertTrue(evalLike("hello world", "%world"));
        assertTrue(evalLike("world", "%world"));
    }

    @Test
    public void testEvalValue_suffixNoMatch() {
        assertFalse(evalLike("hello world", "%hello"));
    }

    // --- Single character wildcard: _ ---

    @Test
    public void testEvalValue_singleCharWildcard() {
        assertTrue(evalLike("abc", "a_c"));
        assertTrue(evalLike("axc", "a_c"));
        assertFalse(evalLike("ac", "a_c"));
        assertFalse(evalLike("abbc", "a_c"));
    }

    @Test
    public void testEvalValue_multipleUnderscores() {
        assertTrue(evalLike("abcd", "a__d"));
        assertFalse(evalLike("abd", "a__d"));
        assertFalse(evalLike("abcxd", "a__d"));
    }

    // --- Mixed % and _ ---

    @Test
    public void testEvalValue_mixedWildcards() {
        // %a_c% means: anything, then 'a', then any single char, then 'c', then anything
        assertTrue(evalLike("xxaxcyy", "%a_c%"));
        assertTrue(evalLike("abc", "%a_c%"));
        assertFalse(evalLike("xxacyy", "%a_c%"));
    }

    @Test
    public void testEvalValue_underscoreAndPercent() {
        // _ello% means: any single char, then "ello", then anything
        assertTrue(evalLike("hello world", "_ello%"));
        assertTrue(evalLike("jello", "_ello%"));
        assertFalse(evalLike("ello", "_ello%"));
    }

    // --- Null handling ---

    @Test
    public void testEvalValue_nullValue() {
        assertFalse(evalLike(null, "%anything%"));
        assertFalse(evalLike(null, ""));
        assertFalse(evalLike(null, "%"));
    }

    @Test
    public void testEvalValue_nullPattern() {
        // null pattern is treated as empty string -> only matches empty string
        SqlLikeExp exp = new SqlLikeExp(new SelectColumn("COL", 0, null), null);
        // Verify the pattern field is set to "" for null
        assertTrue(evalLike("", null));
        assertFalse(evalLike("anything", null));
    }

    // --- Case insensitivity ---

    @Test
    public void testEvalValue_caseInsensitive() {
        assertTrue(evalLike("Hello World", "%hello%"));
        assertTrue(evalLike("hello world", "%HELLO%"));
        assertTrue(evalLike("HELLO WORLD", "%hello world%"));
        assertTrue(evalLike("Hello World", "hello world"));
    }

    @Test
    public void testEvalValue_caseInsensitiveWithWildcards() {
        assertTrue(evalLike("Hello", "h_llo"));
        assertTrue(evalLike("HELLO", "h%o"));
        assertTrue(evalLike("Hello World", "%WORLD"));
        assertTrue(evalLike("Hello World", "HELLO%"));
    }

    // --- Regex special characters in values ---

    @Test
    public void testEvalValue_dotInValue() {
        assertTrue(evalLike("test.value", "%test.value%"));
        // dot in pattern should not act as regex wildcard
        assertFalse(evalLike("testXvalue", "%test.value%"));
    }

    @Test
    public void testEvalValue_plusInValue() {
        assertTrue(evalLike("a+b", "%a+b%"));
        assertFalse(evalLike("aab", "%a+b%"));
    }

    @Test
    public void testEvalValue_parensInValue() {
        assertTrue(evalLike("(test)", "%(test)%"));
        assertFalse(evalLike("test", "%(test)%"));
    }

    @Test
    public void testEvalValue_bracketsInValue() {
        assertTrue(evalLike("a[0]", "%a[0]%"));
        assertFalse(evalLike("a0", "%a[0]%"));
    }

    @Test
    public void testEvalValue_questionMarkInPattern() {
        // ? in LIKE pattern should be literal, not regex quantifier
        assertTrue(evalLike("hello?", "hello?"));
        assertFalse(evalLike("hello", "hello?"));
    }

    @Test
    public void testEvalValue_asteriskInPattern() {
        // * in LIKE pattern should be literal, not regex quantifier
        assertTrue(evalLike("a*b", "a*b"));
        assertFalse(evalLike("ab", "a*b"));
    }

    @Test
    public void testEvalValue_pipeInPattern() {
        assertTrue(evalLike("a|b", "%a|b%"));
        assertFalse(evalLike("a", "%a|b%"));
    }

    @Test
    public void testEvalValue_caretAndDollarInPattern() {
        assertTrue(evalLike("a^b$c", "%a^b$c%"));
    }

    @Test
    public void testEvalValue_backslashInValue() {
        assertTrue(evalLike("a\\b", "%a\\b%"));
    }

    @Test
    public void testEvalValue_bracesInValue() {
        assertTrue(evalLike("a{2}", "%a{2}%"));
        assertFalse(evalLike("aa", "%a{2}%"));
    }

    // --- Empty string and empty pattern ---

    @Test
    public void testEvalValue_emptyString() {
        assertTrue(evalLike("", ""));
        assertTrue(evalLike("", "%"));
        assertFalse(evalLike("", "_"));
        assertFalse(evalLike("", "a%"));
    }

    @Test
    public void testEvalValue_emptyPatternMatchesOnlyEmpty() {
        assertTrue(evalLike("", ""));
        assertFalse(evalLike("anything", ""));
    }

    // --- Exact match (no wildcards) ---

    @Test
    public void testEvalValue_exactMatch() {
        assertTrue(evalLike("hello", "hello"));
        assertFalse(evalLike("hello world", "hello"));
        assertFalse(evalLike("hell", "hello"));
    }

    @Test
    public void testEvalValue_exactMatchCaseInsensitive() {
        assertTrue(evalLike("Hello", "hELLO"));
        assertTrue(evalLike("ABC", "abc"));
    }

    // --- Non-string object toString conversion ---

    @Test
    public void testEvalValue_numericValue() {
        assertTrue(evalLike(12345, "%234%"));
        assertTrue(evalLike(12345, "12345"));
        assertFalse(evalLike(12345, "123456"));
    }

    @Test
    public void testEvalValue_percentOnlyMatchesEverything() {
        assertTrue(evalLike("anything at all", "%"));
        assertTrue(evalLike("", "%"));
        assertTrue(evalLike("   ", "%"));
    }
}
