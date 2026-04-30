package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

/**
 * Controlled proxy for an object facade.
 *
 * <p>Only allows descriptor-declared method calls. Blocks reflection,
 * getClass(), private fields, Spring Bean access, and all undeclared
 * attributes.</p>
 *
 * <p>Method calls are timeout-guarded and error-sanitized.
 * Return values are validated against safe types.</p>
 *
 * <p>Mirrors Python {@code ObjectFacadeProxy} with {@code __getattribute__}
 * total interception.</p>
 *
 * @since 8.4.0
 */
public final class ObjectFacadeProxy {

    /** Types considered safe for return values (v1.7). */
    private static final Set<Class<?>> SAFE_RETURN_TYPES = Set.of(
            Boolean.class, Integer.class, Long.class, Float.class, Double.class,
            String.class, Map.class, List.class
    );

    private final ObjectFacadeDescriptor descriptor;
    private final Object target;
    private final CapabilityPolicy policy;
    private final Map<String, MethodDescriptor> methodMap;

    public ObjectFacadeProxy(
            ObjectFacadeDescriptor descriptor,
            Object target,
            CapabilityPolicy policy) {
        this.descriptor = descriptor;
        this.target = target;
        this.policy = policy;
        this.methodMap = new HashMap<>();
        for (MethodDescriptor m : descriptor.getMethods()) {
            this.methodMap.put(m.getName(), m);
        }
    }

    /**
     * Invoke a declared method by name.
     *
     * @param methodName the method to call
     * @param args       arguments to pass
     * @return the method's return value (safe types only)
     */
    public Object invoke(String methodName, Object... args) {
        // Block dunder / private access
        if (methodName.startsWith("_")) {
            throw new CapabilityException.MethodNotDeclared(
                    "Access to '" + methodName + "' is denied on object '"
                            + descriptor.getObjectName() + "'.");
        }

        // Block reflection / dangerous methods
        if ("getClass".equals(methodName) || "hashCode".equals(methodName)
                || "notify".equals(methodName) || "notifyAll".equals(methodName)
                || "wait".equals(methodName) || "clone".equals(methodName)
                || "finalize".equals(methodName)) {
            throw new CapabilityException.MethodNotDeclared(
                    "Access to '" + methodName + "' is denied on object '"
                            + descriptor.getObjectName() + "'.");
        }

        // Look up in declared methods
        MethodDescriptor methodDesc = methodMap.get(methodName);
        if (methodDesc == null) {
            throw new CapabilityException.MethodNotDeclared(
                    "Method '" + methodName + "' is not declared on object '"
                            + descriptor.getObjectName() + "'.");
        }

        // Check policy allows this object + method
        String objName = descriptor.getObjectName();
        if (!policy.isMethodAllowed(objName, methodName)) {
            throw new CapabilityException.NotAllowed(
                    "Method '" + methodName + "' on object '" + objName
                            + "' is not allowed by the current policy.");
        }

        // Check auth scope
        if (!policy.isScopeAllowed(methodDesc.getAuthScope())) {
            throw new CapabilityException.NotAllowed(
                    "Auth scope '" + methodDesc.getAuthScope()
                            + "' is not allowed by the current policy.");
        }

        // Execute with timeout
        return executeWithTimeout(methodDesc, args);
    }

    private Object executeWithTimeout(MethodDescriptor methodDesc, Object[] args) {
        String methodName = methodDesc.getName();
        int timeoutMs = methodDesc.getTimeoutMs();

        // Capture the current run context for child thread propagation (P2.5)
        com.foggyframework.dataset.db.model.engine.compose.runtime.ScriptRunContext
                parentRunCtx = com.foggyframework.dataset.db.model.engine.compose.runtime
                .ScriptRunContextHolder.current();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "capability-facade-" + descriptor.getObjectName() + "." + methodName);
            t.setDaemon(true);
            return t;
        });

        try {
            Future<Object> future = executor.submit(() -> {
                // Propagate run context to child thread (P2.5)
                com.foggyframework.dataset.db.model.engine.compose.runtime.ScriptRunContextHolder.Token
                        ctxToken = null;
                if (parentRunCtx != null) {
                    ctxToken = com.foggyframework.dataset.db.model.engine.compose.runtime
                            .ScriptRunContextHolder.set(parentRunCtx);
                }
                try {
                    // Find and invoke the method on target
                    Method[] methods = target.getClass().getMethods();
                    for (Method m : methods) {
                        if (m.getName().equals(methodName) && m.getParameterCount() == (args == null ? 0 : args.length)) {
                            m.setAccessible(true);
                            return m.invoke(target, args);
                        }
                    }
                    // Fallback: find by name only
                    for (Method m : methods) {
                        if (m.getName().equals(methodName)) {
                            m.setAccessible(true);
                            return m.invoke(target, args);
                        }
                    }
                    throw new NoSuchMethodException(methodName);
                } finally {
                    if (ctxToken != null) {
                        com.foggyframework.dataset.db.model.engine.compose.runtime
                                .ScriptRunContextHolder.pop(ctxToken);
                    }
                }
            });

            Object result;
            try {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new CapabilityException.Timeout(
                        "Method '" + methodName + "' on object '"
                                + descriptor.getObjectName() + "' exceeded timeout.");
            } catch (ExecutionException e) {
                // P2.5: Pass through ScriptSuspendException without sanitizing
                Throwable cause = e.getCause();
                if (cause instanceof java.lang.reflect.InvocationTargetException ite) {
                    cause = ite.getCause();
                }
                if (cause instanceof com.foggyframework.dataset.db.model.engine.compose.runtime
                        .ScriptSuspendException sse) {
                    throw sse;
                }
                // Sanitize — do not expose internal details
                throw new CapabilityException.MethodNotDeclared(
                        "Method '" + methodName + "' on object '"
                                + descriptor.getObjectName() + "' raised an error during execution.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CapabilityException.Timeout(
                        "Method '" + methodName + "' on object '"
                                + descriptor.getObjectName() + "' was interrupted.");
            }

            // Validate return type
            if (!isSafeReturnValue(result)) {
                throw new CapabilityException.ReturnTypeDenied(
                        "Method '" + methodName + "' on object '"
                                + descriptor.getObjectName() + "' returned a value of disallowed type.");
            }
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    public static boolean isSafeReturnValue(Object value) {
        if (value == null) return true;
        Class<?> clazz = value.getClass();

        // Primitives / wrappers / String
        if (clazz == Boolean.class || clazz == Integer.class || clazz == Long.class
                || clazz == Float.class || clazz == Double.class || clazz == String.class) {
            return true;
        }

        // Map: check keys are String and values are safe (1 level)
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) return false;
                if (!isPrimitiveSafe(entry.getValue())) return false;
            }
            return true;
        }

        // List: check items are safe (1 level)
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (!isPrimitiveSafe(item)) return false;
            }
            return true;
        }

        return false;
    }

    private static boolean isPrimitiveSafe(Object value) {
        if (value == null) return true;
        Class<?> clazz = value.getClass();
        return clazz == Boolean.class || clazz == Integer.class || clazz == Long.class
                || clazz == Float.class || clazz == Double.class || clazz == String.class
                || value instanceof Map || value instanceof List;
    }

    /** Get the list of declared method names (for script dir() equivalent). */
    public List<String> getDeclaredMethodNames() {
        return List.copyOf(methodMap.keySet());
    }

    @Override
    public String toString() {
        return "<ObjectFacadeProxy '" + descriptor.getObjectName() + "'>";
    }
}
