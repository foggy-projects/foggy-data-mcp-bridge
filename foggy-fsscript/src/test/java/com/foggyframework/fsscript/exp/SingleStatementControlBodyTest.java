package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SingleStatementControlBodyTest {
    static Stream<Arguments> scripts() {
        return Stream.of(
            Arguments.of("if(true) probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('no')", ""),
            Arguments.of("if(true) probe.hit('yes');", "yes"),
            Arguments.of("if(true) probe.hit('yes')\n", "yes"),
            Arguments.of("if(true) probe.hit('yes') // end", "yes"),
            Arguments.of("if(false) probe.hit('no')\nprobe.hit('after')", "after"),
            Arguments.of("if(true) probe.hit('yes')\nprobe.hit('after')", "yes,after"),
            Arguments.of("if(true)\nprobe.hit('yes')", "yes"),
            Arguments.of("if(false)\nprobe.hit('no')\nprobe.hit('after')", "after"),
            Arguments.of("if(true)\r\nprobe.hit('yes')\r\nprobe.hit('after')", "yes,after"),
            Arguments.of("if(true)\rprobe.hit('yes')\rprobe.hit('after')", "yes,after"),
            Arguments.of("if(true)\u2028probe.hit('yes')\u2029probe.hit('after')", "yes,after"),
            Arguments.of("if(true) /* header\n comment */ probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('no') /* end\n comment */ probe.hit('after')", "after"),
            Arguments.of("if(false) probe.hit('no')\nelse probe.hit('yes')", "yes"),
            Arguments.of("if(true) probe.hit('yes')\nelse probe.hit('no')", "yes"),
            Arguments.of("if(false) probe.hit('no'); else probe.hit('yes');", "yes"),
            Arguments.of("if(false) probe.hit('no') // comment\nelse probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('no') /* comment\n */ else probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('a'); else if(true) probe.hit('b'); else probe.hit('c')", "b"),
            Arguments.of("if(false) probe.hit('a')\nelse if(false) probe.hit('b')\nelse probe.hit('c')", "c"),
            Arguments.of("if(true) { probe.hit('a') } else probe.hit('b')", "a"),
            Arguments.of("if(false) probe.hit('a'); else { probe.hit('b') }", "b"),
            Arguments.of("if(true)\n{ probe.hit('a') }\nelse\n{ probe.hit('b') }", "a"),
            Arguments.of("if(false) {} else if(false) {} else probe.hit('c')", "c"),
            Arguments.of("if(true) if(false) probe.hit('a'); else probe.hit('b')", "b"),
            Arguments.of("if(false) if(true) probe.hit('a'); else probe.hit('b')", ""),
            Arguments.of("if(true) if(true) probe.hit('a'); else probe.hit('b')", "a"),
            Arguments.of("if(true) if(false) probe.hit('a')\nelse probe.hit('b')", "b"),
            Arguments.of("if(false) if(false) probe.hit('a'); else probe.hit('b'); else probe.hit('c')", "c"),
            Arguments.of("if(true) { if(false) probe.hit('a') } else probe.hit('b')", ""),
            Arguments.of("if(false) { if(true) probe.hit('a') } else probe.hit('b')", "b"),
            Arguments.of("if(true); probe.hit('after')", "after"),
            Arguments.of("if(false); else probe.hit('yes')", "yes"),
            Arguments.of("for(let i=0;i<3;i++) probe.hit(i)", "0,1,2"),
            Arguments.of("for(let i=0;i<3;i++)\nprobe.hit(i)\nprobe.hit('after')", "0,1,2,after"),
            Arguments.of("for(const x of [1,2]) probe.hit(x)", "1,2"),
            Arguments.of("while(probe.more()) probe.hit('loop')", "loop,loop,loop"),
            Arguments.of("while(probe.more())\nprobe.hit('loop')\nprobe.hit('after')", "loop,loop,loop,after"),
            Arguments.of("for(let i=0;i<3;i++) if(i==1) probe.hit(i)", "1"),
            Arguments.of("if(true) for(let i=0;i<2;i++) if(i==0) probe.hit('a'); else probe.hit('b')", "a,b"),
            Arguments.of("for(let i=0;i<4;i++){ if(i==1) continue; if(i==3) break; probe.hit(i) }", "0,2"),
            Arguments.of("for(let i=0;i<4;i++){ if(i==1) continue\nif(i==3) break\nprobe.hit(i) }", "0,2"),
            Arguments.of("function f(x){if(x) return 7; return 9;} probe.hit(f(true));probe.hit(f(false))", "7,9"),
            Arguments.of("function f(x){if(x) return 7\nreturn 9;} probe.hit(f(true));probe.hit(f(false))", "7,9"),
            Arguments.of("try { if(true) throw 'boom' } catch(e) { probe.hit(e) }", "boom"),
            Arguments.of("let x=1; if(true) x+=2\nprobe.hit(x)", "3"),
            Arguments.of("let x=1; if(false) x*=2\nprobe.hit(x)", "1"),
            Arguments.of("let x=null;if(true) x={key:3}\nelse x={key:4}\nprobe.hit(x.key)", "3"),
            Arguments.of("if(true) probe\n.hit('a')", "a"),
            Arguments.of("if(true) probe.hit\n('a')", "a"),
            Arguments.of("if(true) probe.hit(1\n+2)", "3"),
            Arguments.of("if(probe.truth())\nprobe.hit('a')", "a"),
            Arguments.of("if(true) probe.hit('a'); probe.hit('after')", "a,after"),
            Arguments.of("function f(){ if(true) probe.hit('a') } f()", "a"),
            Arguments.of("if(false) probe.hit('no'); else while(probe.more()) probe.hit('yes')", "yes,yes,yes"),
            Arguments.of("let x=0; if(true) x++\nprobe.hit(x)", "1"),
            Arguments.of("let x=0; if(false) x++\nelse x--\nprobe.hit(x)", "-1"),
            Arguments.of("let x=0; if(true) x\n+=2\nprobe.hit(x)", "2"),
            Arguments.of("let a=[0];if(true) a[0]=3\nprobe.hit(a[0])", "3"),
            Arguments.of("let a=[0];if(true) a[probe.index()]+=3\nprobe.hit(a[0])", "3"),
            Arguments.of("if(true) probe.hit([1,2]\n[1])", "2"),
            Arguments.of("if(true) probe.hit(true\n? 'yes' : 'no')", "yes"),
            Arguments.of("if(true) probe.hit('else if { ; }')", "else if { ; }"),
            Arguments.of("if(true) probe.hit(1+\n2*3)", "7"),
            Arguments.of("if(false) probe.hit('a')\nelse\nprobe.hit('b')\nprobe.hit('after')", "b,after"),
            Arguments.of("if(true) if(false) probe.hit('a'); else if(true) probe.hit('b'); else probe.hit('c')", "b"),
            Arguments.of("if(false) if(false) probe.hit('a'); else if(true) probe.hit('b'); else probe.hit('c')", ""),
            Arguments.of("if(false) for(let i=0;i<2;i++) probe.hit(i); else probe.hit('else')", "else"),
            Arguments.of("for(let i=0;i<2;i++) for(let j=0;j<2;j++) probe.hit(i*2+j)", "0,1,2,3"),
            Arguments.of("for(let i=0;i<3;i++)\n{if(i==1) continue\nprobe.hit(i)}", "0,2"),
            Arguments.of("while(probe.more()) if(true) break\nprobe.hit('after')", "after"),
            Arguments.of("while(probe.more()) if(false) probe.hit('no'); else probe.hit('yes')", "yes,yes,yes"),
            Arguments.of("if(true) switch(1){case 1: probe.hit('one'); break; default: probe.hit('bad');}", "one"),
            Arguments.of("if(true) try{throw 'boom'}catch(e){probe.hit(e)}", "boom"),
            Arguments.of("try{if(false) throw 'bad'\nprobe.hit('ok')}finally{probe.hit('done')}", "ok,done"),
            Arguments.of("function f(){if(true) return\nprobe.hit('bad');} f();probe.hit('after')", "after"),
            Arguments.of("function f(){if(true) return /* newline\n */ probe.hit('bad');} f();probe.hit('after')", "after"),
            Arguments.of("function f(){if(true) return} f();probe.hit('after')", "after"),
            Arguments.of("if((()=>{if(true) probe.hit('inner')\nreturn true;})()) probe.hit('outer')", "inner,outer"),
            Arguments.of("const o={'if': x=>probe.hit(x)};if(true) o.if('yes')\nprobe.hit('after')", "yes,after"),
            Arguments.of("if(true) probe.hit('a')\nif(false) probe.hit('b')\nif(true) probe.hit('c')", "a,c"),
            Arguments.of("if(false) probe.hit('a');else if(false) probe.hit('b');else if(true) probe.hit('c');else probe.hit('d')", "c"),
            Arguments.of("if(true) { const x=2; probe.hit(x) }", "2"),
            Arguments.of("let f=null;if(true) f=x=>x*2;probe.hit(f(3))", "6"),
            Arguments.of("let o={};if(false) o={a:1};else o={a:2}\nprobe.hit(o.a)", "2"),
            Arguments.of("let x=2;if(true) x--\nprobe.hit(x)", "1"),
            Arguments.of("let x=2;if(false) x--\nprobe.hit(x)", "2"),
            Arguments.of("let x=2;if(true) ++x\nprobe.hit(x)", "3"),
            Arguments.of("let x=2;if(true) --x\nprobe.hit(x)", "1"),
            Arguments.of("let x=2;if(true) x\n++x\nprobe.hit(x)", "3"),
            Arguments.of("let x=2;if(false) x\n--x\nprobe.hit(x)", "1"),
            Arguments.of("let x=2;if(true) ++\nx\nprobe.hit(x)", "3"),
            Arguments.of("let x=2;if(true) --\nx\nprobe.hit(x)", "1"),
            Arguments.of("let x=2;if(false) ++\nx\nprobe.hit(x)", "2"),
            Arguments.of("let f=null;if(true) f=x=>x*2\nprobe.hit(f(3))", "6"),
            Arguments.of("let f=null;if(false) f=x=>x*2\nelse f=x=>x*3\nprobe.hit(f(3))", "9"),
            Arguments.of("let x=2;if(true) probe.hit(x++ + 1)\nprobe.hit(x)", "3,3"),
            Arguments.of("let x=2;if(true) probe.hit(++x * 2)\nprobe.hit(x)", "6,3"),
            Arguments.of("let x=2;if(true) probe.hit(x-- + 1)\nprobe.hit(x)", "3,1"),
            Arguments.of("let x=2;if(true) probe.hit(--x * 2)\nprobe.hit(x)", "2,1"),
            Arguments.of("if(false) probe.hit('no') /* *\n */ else probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('no') // comment\u2028else probe.hit('yes')", "yes"),
            Arguments.of("if(false) probe.hit('no') // comment\u2029else probe.hit('yes')", "yes"),
            Arguments.of("if(true) probe.hit('yes') // comment\t probe.hit('bad')\nprobe.hit('after')", "yes,after")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scripts")
    void evaluatesExactlyOneBody(String script, String expected) {
        Probe probe = new Probe();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("probe", probe);
        compile(script).evalValue(evaluator);
        assertEquals(expected.isEmpty() ? List.of() : List.of(expected.split(",")), probe.calls);
    }

    static Stream<String> invalidScripts() {
        return Stream.of(
            "if(true)", "if(true)\n", "if(true) else probe.hit('x')",
            "if(true) probe.hit('a') else probe.hit('b')",
            "if(true) probe.hit('a') probe.hit('b')",
            "if(true) probe.hit('a'); else",
            "function f() probe.hit('x')",
            "try probe.hit('x'); catch(e) {}",
            "try {} catch(e) probe.hit('x')",
            "if(true) throw\n'boom'",
            "for(let i=0\ni<3;i++) probe.hit(i)",
            "for(let i=0;i<3\ni++) probe.hit(i)",
            "if(true) let x=1;", "if(true) const x=1;",
            "if(true) probe.hit('a') /* no newline */ else probe.hit('b')",
            "if(true) throw /* newline\n */ 'boom'",
            "if(true)\nelse probe.hit('bad')",
            "while(true)\n", "for(let i=0;i<1;i++)\n",
            "for(let i=0;i<1\n) probe.hit(i)",
            "for(let i=0;i<1;i\n++) probe.hit(i)",
            "for(let i=1;i>0;i\n--) probe.hit(i)"
        );
    }

    @ParameterizedTest(name = "reject {0}")
    @MethodSource("invalidScripts")
    void rejectsMissingBodiesAndIllegalTerminators(String script) {
        assertThrows(RuntimeException.class, () -> compile(script));
    }

    private static com.foggyframework.fsscript.parser.spi.Exp compile(String script) {
        return ExpUtils.compileEl(new SimpleFsscriptClosureDefinitionSpace()
                .newFsscriptClosureDefinition(), script, null);
    }

    @Test
    void conditionAndSubscriptAreEvaluatedOnce() {
        Probe probe = new Probe();
        DefaultExpEvaluator evaluator = DefaultExpEvaluator.newInstance();
        evaluator.setVar("probe", probe);
        compile("let a=[0];if(probe.truth()) a[probe.index()]+=3\nprobe.hit(a[0])").evalValue(evaluator);
        assertEquals(1, probe.conditions);
        assertEquals(1, probe.indices);
        assertEquals(List.of("3"), probe.calls);
    }

    @Test
    void parserCanBeReusedAfterAsiAtEof() {
        var parser = new com.foggyframework.fsscript.parser.ExpParser();
        assertNotNull(parser.compileEl("if(true) 1"));
        assertNotNull(parser.compileEl("if(false) 1; else 2"));
        assertThrows(RuntimeException.class, () -> parser.compileEl("if(true)"));
        assertNotNull(parser.compileEl("if(true) 3"));
    }

    @Test
    void templateScannerAcceptsControlBlocksAndSingleStatements() {
        var parser = new com.foggyframework.fsscript.parser.ExpParser();
        assertNotNull(parser.compile("${if(true){1}}"));
        assertNotNull(parser.compile("${if(true) 1;}"));
    }

    public static class Probe {
        final List<String> calls = new ArrayList<>();
        int checks;
        int conditions;
        int indices;
        public Object hit(Object value) {
            if (calls.size() > 100) throw new IllegalStateException("Unexpected loop");
            calls.add(value instanceof Number number
                    ? new java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
                    : String.valueOf(value));
            return value;
        }
        public boolean more() { return ++checks <= 3; }
        public boolean truth() { conditions++; return true; }
        public int index() { indices++; return 0; }
    }
}
