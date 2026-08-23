package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.spi.QueryModel;

/**
 * Host-owned authority bridge for one exact query-model projection.
 *
 * <p>The implementation validates the opaque Analytics authority binding and
 * returns a trusted context containing product-owned identity and data governance.
 * The binding reference must not be treated as caller-supplied filter JSON.</p>
 */
@FunctionalInterface
public interface FoggySemanticRequestContextResolver {

    SemanticRequestContext resolve(
            QueryAuthorityRequest request,
            CatalogResolution<QueryModel> catalogResolution);
}
