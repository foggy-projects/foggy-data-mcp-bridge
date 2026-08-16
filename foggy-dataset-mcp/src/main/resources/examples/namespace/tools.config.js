// Copy this file to the root of a namespace model bundle as tools.config.js.
// Keep null to expose every tool already allowed by Foggy's built-in role rules.
const policyUrl = null;

const resolveTools = function(context) {
    if (policyUrl == null) {
        return context.registeredTools;
    }

    const result = post({
        url: policyUrl,
        headers: {
            Authorization: context.authorization,
            "Content-Type": "application/json"
        },
        data: {
            namespace: context.namespace,
            userRole: context.userRole,
            traceId: context.traceId,
            registeredTools: context.registeredTools
        },
        returnClass: "java.util.Map"
    });
    return result.enabledTools;
};

export default resolveTools;
