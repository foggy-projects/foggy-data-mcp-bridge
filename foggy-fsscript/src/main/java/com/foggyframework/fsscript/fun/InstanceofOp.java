/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved.
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.fsscript.fun;

import com.foggyframework.fsscript.builtin.ArrayGlobal;
import com.foggyframework.fsscript.builtin.BuiltinGlobalExp;
import com.foggyframework.fsscript.exp.IdExp;
import com.foggyframework.fsscript.exp.ImportStaticClassExp;
import com.foggyframework.fsscript.parser.FunDef;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JavaScript instanceof operator implementation.
 *
 * Supports:
 * - Imported Java classes: obj instanceof MyClass
 * - Built-in type names: obj instanceof String / Number / Date / Array / Map / Set / Boolean
 * - Class objects: obj instanceof someClassVar
 */
public class InstanceofOp implements FunDef {

    @Override
    public Object execute(ExpEvaluator evaluator, Exp[] args) {
        Object left = args[0].evalResult(evaluator);
        if (left == null) {
            return false;
        }

        Class<?> targetClass = resolveClass(evaluator, args[1]);
        return targetClass.isInstance(left);
    }

    private Class<?> resolveClass(ExpEvaluator evaluator, Exp rightExp) {
        // 1. Built-in type name — check identifier before evaluating,
        //    because names like "Array" may be bound to global objects (ArrayGlobal)
        String typeName = getTypeName(rightExp);
        if (typeName != null) {
            Class<?> builtin = resolveBuiltinType(typeName);
            if (builtin != null) {
                return builtin;
            }
        }

        // 2. Evaluate and resolve
        Object right = rightExp.evalResult(evaluator);

        // import 'java:...' class reference
        if (right instanceof ImportStaticClassExp.StaticClassPropertyFunction) {
            return ((ImportStaticClassExp.StaticClassPropertyFunction) right).getBeanClass();
        }
        // Already a Class object
        if (right instanceof Class) {
            return (Class<?>) right;
        }
        // ArrayGlobal → List
        if (right instanceof ArrayGlobal) {
            return List.class;
        }

        throw new IllegalArgumentException("instanceof right-hand side must be a type reference, got: " + right);
    }

    /**
     * Extract type name from expression node (before evaluation).
     */
    private String getTypeName(Exp exp) {
        if (exp instanceof IdExp) {
            return ((IdExp) exp).value;
        }
        // BuiltinGlobalExp (Array, JSON, console) — check by reference
        if (exp == BuiltinGlobalExp.ARRAY) {
            return "Array";
        }
        return null;
    }

    private Class<?> resolveBuiltinType(String name) {
        return switch (name) {
            case "String" -> String.class;
            case "Number" -> Number.class;
            case "Integer" -> Integer.class;
            case "Long" -> Long.class;
            case "Double" -> Double.class;
            case "Boolean" -> Boolean.class;
            case "Date" -> Date.class;
            case "Array", "List" -> List.class;
            case "Map", "Object" -> Map.class;
            case "Set" -> Set.class;
            default -> null;  // not a built-in type, will try evaluation
        };
    }

    @Override
    public String getName() {
        return "INSTANCEOF";
    }
}
