package com.foggyframework.dataset.model.engine.compose.compilation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ComposeScalarExpressionTest {
    @Test void rejectsRelationAccessRegardlessOfAliasesOrNesting() {
        for (String expression : List.of("(SELECT amount FROM secret)", "COALESCE((SELECT amount FROM secret), 0)",
                "CASE WHEN id=1 THEN (SELECT amount FROM secret) ELSE 0 END", "id IN (SELECT id FROM secret)",
                "SUM((SELECT amount FROM secret)) OVER ()", "id; SELECT amount FROM secret", "secret.amount", "load_extension('evil')")) {
            assertThrows(ComposeCompileException.class, () -> ComposeScalarExpression.references(expression), expression);
            assertThrows(ComposeCompileException.class, () -> ComposeScalarExpression.render(expression, "sqlite", name -> name), expression);
        }
    }

    @Test void dependenciesComeFromFieldsNotLiteralsFunctionsOrUnits() {
        assertEquals(List.of("SELECT"), ComposeScalarExpression.references("SELECT"));
        assertEquals("\"SELECT\"", ComposeScalarExpression.render("SELECT","sqlite", name -> "\""+name+"\""));
        assertEquals(List.of("start", "end"), ComposeScalarExpression.references("TIMESTAMPDIFF(HOUR,start,end)"));
        assertEquals(List.of("start", "end"), ComposeScalarExpression.references("TIMESTAMPDIFF('HOUR',start,end)"));
        assertEquals(List.of("amount"), ComposeScalarExpression.references("CASE WHEN amount > 0 THEN 'SELECT secret FROM table' ELSE 'none' END"));
        assertEquals(List.of(), ComposeScalarExpression.references("COUNT(*)"));
        assertEquals(List.of("amount", "grp", "id"), ComposeScalarExpression.references("SUM(amount) OVER (PARTITION BY grp ORDER BY id ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)"));
    }

    @Test void quotesIdentifiersWithoutChangingStringLiteralsAndLowersDateFunctions() {
        String sql = ComposeScalarExpression.render("COALESCE(name, 'MixedCase')", "postgres", name -> "\"" + name + "\"");
        assertEquals("COALESCE(\"name\", 'MixedCase')", sql);
        assertEquals("name NOT LIKE 'x!_%' ESCAPE '!'", ComposeScalarExpression.render("name NOT LIKE 'x!_%' ESCAPE '!'", "sqlite", name -> name));
        assertTrue(ComposeScalarExpression.render("DATEDIFF(finish,start)", "sqlite", name -> name).contains("julianday"));
        assertTrue(ComposeScalarExpression.render("TIMESTAMPDIFF(HOUR,start,finish)", "postgres", name -> name).contains("EXTRACT(EPOCH"));
    }
}
