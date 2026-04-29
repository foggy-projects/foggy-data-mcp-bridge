package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Default-empty capability registry.
 *
 * <p>Stores validated descriptors and their renderers/handlers/targets.
 * Registration validates descriptors; duplicate or reserved names are
 * rejected. The registry itself does NOT decide runtime visibility —
 * that is the job of {@link CapabilityPolicy}.</p>
 *
 * <p>Thread safety: not thread-safe. Create per-application or guard
 * externally.</p>
 *
 * <p>Mirrors Python {@code CapabilityRegistry}.</p>
 *
 * @since 8.4.0
 */
public final class CapabilityRegistry {

    private final Map<String, FunctionEntry> functions = new HashMap<>();
    private final Map<String, ObjectEntry> objects = new HashMap<>();

    // ---------------------------------------------------------------
    // Function registration
    // ---------------------------------------------------------------

    /**
     * Register a sql_scalar function.
     */
    public void registerFunction(FunctionDescriptor descriptor, CapabilityFunctionRenderer renderer) {
        String name = descriptor.getName();
        validateUniqueName(name);

        if (!"sql_scalar".equals(descriptor.getKind())) {
            throw new CapabilityException.InvalidDescriptor(
                    "registerFunction(descriptor, renderer) requires kind=sql_scalar; got '" + descriptor.getKind() + "'.");
        }
        if (renderer == null) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "' (sql_scalar): renderer is required.");
        }
        functions.put(name, new FunctionEntry(descriptor, renderer, null));
    }

    /**
     * Register a pure_runtime function.
     */
    public void registerFunction(FunctionDescriptor descriptor, CapabilityFunctionHandler handler) {
        String name = descriptor.getName();
        validateUniqueName(name);

        if (!"pure_runtime".equals(descriptor.getKind())) {
            throw new CapabilityException.InvalidDescriptor(
                    "registerFunction(descriptor, handler) requires kind=pure_runtime; got '" + descriptor.getKind() + "'.");
        }
        if (handler == null) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "' (pure_runtime): handler is required.");
        }
        functions.put(name, new FunctionEntry(descriptor, null, handler));
    }

    // ---------------------------------------------------------------
    // Object facade registration
    // ---------------------------------------------------------------

    /**
     * Register a controlled object facade.
     *
     * @param descriptor validated object facade descriptor
     * @param target     the actual object instance; only descriptor-declared
     *                   methods will be callable
     */
    public void registerObjectFacade(ObjectFacadeDescriptor descriptor, Object target) {
        String objName = descriptor.getObjectName();

        if (objects.containsKey(objName)) {
            throw new CapabilityException.InvalidDescriptor(
                    "Object facade '" + objName + "' is already registered.");
        }
        if (functions.containsKey(objName)) {
            throw new CapabilityException.InvalidDescriptor(
                    "Name '" + objName + "' is already used by a function.");
        }

        // Verify that declared methods actually exist on target.
        for (MethodDescriptor method : descriptor.getMethods()) {
            try {
                // Find any public method with the declared name (any params)
                boolean found = false;
                for (Method m : target.getClass().getMethods()) {
                    if (m.getName().equals(method.getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new CapabilityException.InvalidDescriptor(
                            "Object facade '" + objName + "': declared method '"
                                    + method.getName() + "' not found on target object.");
                }
            } catch (SecurityException e) {
                throw new CapabilityException.InvalidDescriptor(
                        "Object facade '" + objName + "': cannot verify method '"
                                + method.getName() + "' on target object.");
            }
        }

        objects.put(objName, new ObjectEntry(descriptor, target));
    }

    // ---------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------

    public FunctionEntry getFunction(String name) {
        FunctionEntry entry = functions.get(name);
        if (entry == null) {
            throw new CapabilityException.NotRegistered(
                    "Function '" + name + "' is not registered.");
        }
        return entry;
    }

    public ObjectEntry getObject(String name) {
        ObjectEntry entry = objects.get(name);
        if (entry == null) {
            throw new CapabilityException.NotRegistered(
                    "Object '" + name + "' is not registered.");
        }
        return entry;
    }

    public boolean hasFunction(String name) { return functions.containsKey(name); }
    public boolean hasObject(String name)   { return objects.containsKey(name); }
    public boolean isEmpty()                { return functions.isEmpty() && objects.isEmpty(); }
    public Set<String> functionNames()      { return Set.copyOf(functions.keySet()); }
    public Set<String> objectNames()        { return Set.copyOf(objects.keySet()); }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void validateUniqueName(String name) {
        if (functions.containsKey(name)) {
            throw new CapabilityException.InvalidDescriptor(
                    "Function '" + name + "' is already registered.");
        }
        if (objects.containsKey(name)) {
            throw new CapabilityException.InvalidDescriptor(
                    "Name '" + name + "' is already used by an object facade.");
        }
    }

    // ---------------------------------------------------------------
    // Entry records
    // ---------------------------------------------------------------

    public static final class FunctionEntry {
        private final FunctionDescriptor descriptor;
        private final CapabilityFunctionRenderer renderer;
        private final CapabilityFunctionHandler handler;

        FunctionEntry(FunctionDescriptor descriptor,
                      CapabilityFunctionRenderer renderer,
                      CapabilityFunctionHandler handler) {
            this.descriptor = descriptor;
            this.renderer = renderer;
            this.handler = handler;
        }

        public FunctionDescriptor getDescriptor() { return descriptor; }
        public CapabilityFunctionRenderer getRenderer() { return renderer; }
        public CapabilityFunctionHandler getHandler() { return handler; }
    }

    public static final class ObjectEntry {
        private final ObjectFacadeDescriptor descriptor;
        private final Object target;

        ObjectEntry(ObjectFacadeDescriptor descriptor, Object target) {
            this.descriptor = descriptor;
            this.target = target;
        }

        public ObjectFacadeDescriptor getDescriptor() { return descriptor; }
        public Object getTarget() { return target; }
    }
}
