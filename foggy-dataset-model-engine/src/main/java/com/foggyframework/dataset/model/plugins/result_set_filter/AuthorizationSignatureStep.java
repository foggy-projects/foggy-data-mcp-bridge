package com.foggyframework.dataset.model.plugins.result_set_filter;

import com.foggyframework.dataset.model.semantic.permission.AuthorizationSignatureService;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seals the final effective permission snapshot before any data cache lookup.
 */
@Component
@Order(110)
public class AuthorizationSignatureStep implements DataSetResultStep {

    @Resource
    private AuthorizationSignatureService authorizationSignatureService;

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        ctx.setAuthorizationSignature(
                authorizationSignatureService.compute(ctx).orElse(null));
        return CONTINUE;
    }
}
