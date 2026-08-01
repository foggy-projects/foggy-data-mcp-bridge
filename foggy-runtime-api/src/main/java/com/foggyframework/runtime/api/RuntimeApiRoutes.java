package com.foggyframework.runtime.api;

public final class RuntimeApiRoutes {

    public static final String API_V1 = "/api/v1";
    public static final String API_V1_PATTERN = API_V1 + "/**";
    public static final String LEGACY_BUNDLES_API = "/api/bundles";
    public static final String LEGACY_BUNDLES_PATTERN = LEGACY_BUNDLES_API + "/**";

    private RuntimeApiRoutes() {
    }

    public static final class V1 {

        public static final String CAPABILITIES = "/capabilities";
        public static final String ACCESS_CHECK = "/access/check";
        public static final String BUNDLES = "/bundles";
        public static final String BUNDLE_BY_NAME = BUNDLES + "/{name}";
        public static final String DATASOURCES = "/datasources";
        public static final String DATASOURCES_DIAGNOSTICS = DATASOURCES + "/diagnostics";
        public static final String DATASOURCE_BY_NAME = DATASOURCES + "/{name}";
        public static final String DATASOURCE_TEST = DATASOURCE_BY_NAME + "/test";
        public static final String NAMESPACE_DATASOURCE = "/namespaces/{namespace}/datasource";
        public static final String RESOURCES_EXPORT = "/resources/export";
        public static final String RESOURCES_SAVE = "/resources/save";
        public static final String MODELS = "/models";
        public static final String MODEL_DESCRIBE = MODELS + "/{model}/describe";
        public static final String MODELS_VALIDATE = MODELS + "/validate";
        public static final String MODELS_REFRESH = MODELS + "/refresh";
        public static final String QUERY_VALIDATE = "/query/{model}/validate";
        public static final String QUERY_EXECUTE = "/query/{model}/execute";
        public static final String TABLES_LIST = "/tables/list";
        public static final String TABLES_INSPECT = "/tables/inspect";
        public static final String SQL_QUERY = "/sql/query";
        public static final String COMPOSE = "/compose";
        public static final String COMPOSE_VALIDATE = COMPOSE + "/validate";
        public static final String COMPOSE_PREVIEW = COMPOSE + "/preview";
        public static final String COMPOSE_EXECUTE = COMPOSE + "/execute";
        public static final String FSSCRIPT = "/fsscript";
        public static final String FSSCRIPT_EXECUTE = FSSCRIPT + "/execute";
        public static final String AUTHORING_WORKSPACES = "/authoring/workspaces";
        public static final String AUTHORING = "/authoring";
        public static final String AUTHORING_RELEASES = AUTHORING + "/releases";
        public static final String AUTHORING_RELEASE_IMPORT =
                AUTHORING_RELEASES + "/import";
        public static final String AUTHORING_WORKSPACE =
                AUTHORING_WORKSPACES + "/{workspaceId}";
        public static final String AUTHORING_RESOURCES =
                AUTHORING_WORKSPACE + "/resources";
        public static final String AUTHORING_RESOURCE_CONTENT =
                AUTHORING_RESOURCES + "/content";
        public static final String AUTHORING_RESOURCES_SAVE =
                AUTHORING_RESOURCES + "/save";
        public static final String AUTHORING_RESOURCES_DELETE =
                AUTHORING_RESOURCES + "/delete";
        public static final String AUTHORING_DIFF =
                AUTHORING_WORKSPACE + "/diff";
        public static final String AUTHORING_VALIDATE =
                AUTHORING_WORKSPACE + "/validate";
        public static final String AUTHORING_PUBLISH =
                AUTHORING_WORKSPACE + "/publish";
        public static final String AUTHORING_PUBLISH_RECOVER =
                AUTHORING_PUBLISH + "/recover";
        public static final String AUTHORING_RELEASE_EXPORT =
                AUTHORING_WORKSPACE + "/release-package";
        public static final String AUTHORING_PROMOTE =
                AUTHORING_WORKSPACE + "/promote";
        public static final String AUTHORING_ROLLBACK =
                AUTHORING_WORKSPACE + "/rollback";
        public static final String AUTHORING_ROLLBACK_RECOVER =
                AUTHORING_ROLLBACK + "/recover";
        public static final String AUTHORING_QUERY_VALIDATE =
                AUTHORING_WORKSPACE + "/query/{model}/validate";
        public static final String AUTHORING_QUERY_EXECUTE =
                AUTHORING_WORKSPACE + "/query/{model}/execute";

        private V1() {
        }
    }

    public static final class Full {

        public static final String CAPABILITIES = API_V1 + V1.CAPABILITIES;
        public static final String ACCESS_CHECK = API_V1 + V1.ACCESS_CHECK;
        public static final String BUNDLES = API_V1 + V1.BUNDLES;
        public static final String BUNDLE_BY_NAME = API_V1 + V1.BUNDLE_BY_NAME;
        public static final String DATASOURCES = API_V1 + V1.DATASOURCES;
        public static final String DATASOURCES_DIAGNOSTICS = API_V1 + V1.DATASOURCES_DIAGNOSTICS;
        public static final String DATASOURCE_BY_NAME = API_V1 + V1.DATASOURCE_BY_NAME;
        public static final String DATASOURCE_TEST = API_V1 + V1.DATASOURCE_TEST;
        public static final String NAMESPACE_DATASOURCE = API_V1 + V1.NAMESPACE_DATASOURCE;
        public static final String RESOURCES_EXPORT = API_V1 + V1.RESOURCES_EXPORT;
        public static final String RESOURCES_SAVE = API_V1 + V1.RESOURCES_SAVE;
        public static final String MODELS = API_V1 + V1.MODELS;
        public static final String MODEL_DESCRIBE = API_V1 + V1.MODEL_DESCRIBE;
        public static final String MODELS_VALIDATE = API_V1 + V1.MODELS_VALIDATE;
        public static final String MODELS_REFRESH = API_V1 + V1.MODELS_REFRESH;
        public static final String QUERY_VALIDATE = API_V1 + V1.QUERY_VALIDATE;
        public static final String QUERY_EXECUTE = API_V1 + V1.QUERY_EXECUTE;
        public static final String TABLES_LIST = API_V1 + V1.TABLES_LIST;
        public static final String TABLES_INSPECT = API_V1 + V1.TABLES_INSPECT;
        public static final String SQL_QUERY = API_V1 + V1.SQL_QUERY;
        public static final String COMPOSE_VALIDATE = API_V1 + V1.COMPOSE_VALIDATE;
        public static final String COMPOSE_PREVIEW = API_V1 + V1.COMPOSE_PREVIEW;
        public static final String COMPOSE_EXECUTE = API_V1 + V1.COMPOSE_EXECUTE;
        public static final String FSSCRIPT_EXECUTE = API_V1 + V1.FSSCRIPT_EXECUTE;
        public static final String AUTHORING_WORKSPACES =
                API_V1 + V1.AUTHORING_WORKSPACES;
        public static final String AUTHORING_PATTERN =
                API_V1 + V1.AUTHORING + "/**";
        public static final String AUTHORING_WORKSPACES_PATTERN =
                AUTHORING_WORKSPACES + "/**";
        public static final String AUTHORING_WORKSPACE =
                API_V1 + V1.AUTHORING_WORKSPACE;
        public static final String AUTHORING_RESOURCES =
                API_V1 + V1.AUTHORING_RESOURCES;
        public static final String AUTHORING_RESOURCE_CONTENT =
                API_V1 + V1.AUTHORING_RESOURCE_CONTENT;
        public static final String AUTHORING_RESOURCES_SAVE =
                API_V1 + V1.AUTHORING_RESOURCES_SAVE;
        public static final String AUTHORING_RESOURCES_DELETE =
                API_V1 + V1.AUTHORING_RESOURCES_DELETE;
        public static final String AUTHORING_DIFF = API_V1 + V1.AUTHORING_DIFF;
        public static final String AUTHORING_VALIDATE =
                API_V1 + V1.AUTHORING_VALIDATE;
        public static final String AUTHORING_PUBLISH =
                API_V1 + V1.AUTHORING_PUBLISH;
        public static final String AUTHORING_PUBLISH_RECOVER =
                API_V1 + V1.AUTHORING_PUBLISH_RECOVER;
        public static final String AUTHORING_RELEASE_EXPORT =
                API_V1 + V1.AUTHORING_RELEASE_EXPORT;
        public static final String AUTHORING_RELEASE_IMPORT =
                API_V1 + V1.AUTHORING_RELEASE_IMPORT;
        public static final String AUTHORING_PROMOTE =
                API_V1 + V1.AUTHORING_PROMOTE;
        public static final String AUTHORING_ROLLBACK =
                API_V1 + V1.AUTHORING_ROLLBACK;
        public static final String AUTHORING_ROLLBACK_RECOVER =
                API_V1 + V1.AUTHORING_ROLLBACK_RECOVER;
        public static final String AUTHORING_QUERY_VALIDATE =
                API_V1 + V1.AUTHORING_QUERY_VALIDATE;
        public static final String AUTHORING_QUERY_EXECUTE =
                API_V1 + V1.AUTHORING_QUERY_EXECUTE;
        public static final String LEGACY_BUNDLE_ADD = LEGACY_BUNDLES_API + "/add";
        public static final String LEGACY_BUNDLE_REMOVE = LEGACY_BUNDLES_API + "/remove/{bundleName}";

        private Full() {
        }
    }
}
