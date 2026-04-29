package com.foggyframework.dataset.db.model.engine.compose.capability;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable descriptor for a controlled object facade registration.
 *
 * <p>Mirrors Python {@code ObjectFacadeDescriptor} frozen dataclass.</p>
 *
 * @since 8.4.0
 */
public final class ObjectFacadeDescriptor {

    private final String objectName;
    private final List<MethodDescriptor> methods;

    public ObjectFacadeDescriptor(String objectName, List<MethodDescriptor> methods) {
        FunctionDescriptor.validateName(objectName, "Object facade");

        if (methods == null || methods.isEmpty()) {
            throw new CapabilityException.InvalidDescriptor(
                    "Object facade '" + objectName + "': must declare at least one method.");
        }

        Set<String> seenNames = new HashSet<>();
        for (MethodDescriptor method : methods) {
            if (!seenNames.add(method.getName())) {
                throw new CapabilityException.InvalidDescriptor(
                        "Object facade '" + objectName + "': duplicate method name '" + method.getName() + "'.");
            }
        }

        this.objectName = objectName;
        this.methods = Collections.unmodifiableList(List.copyOf(methods));
    }

    public String getObjectName()           { return objectName; }
    public List<MethodDescriptor> getMethods() { return methods; }
}
