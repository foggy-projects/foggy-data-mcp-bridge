package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import java.time.Instant;

/**
 * Optional host-owned handoff for viewer links that require an authenticated
 * browser session. Implementations must not persist or return the supplied
 * authorization value.
 */
@FunctionalInterface
public interface ViewerLaunchLinkProvider {

    String createViewerUrl(CachedQueryContext query, String authorization, String defaultViewerUrl);

    /** Older providers may not know their link's separate expiration. */
    default ViewerLaunchLink createViewerLink(CachedQueryContext query, String authorization,
                                              String defaultViewerUrl) {
        return new ViewerLaunchLink(createViewerUrl(query, authorization, defaultViewerUrl), null);
    }

    record ViewerLaunchLink(String url, Instant expiresAt) { }
}
