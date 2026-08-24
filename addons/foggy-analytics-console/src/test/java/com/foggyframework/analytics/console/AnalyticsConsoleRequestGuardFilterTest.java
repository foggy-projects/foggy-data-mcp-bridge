package com.foggyframework.analytics.console;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsConsoleRequestGuardFilterTest {

    private final AnalyticsConsoleRequestGuardFilter filter =
            new AnalyticsConsoleRequestGuardFilter();

    @Test
    void rejectsMutationWithoutConsoleRequestHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/assets/drafts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("ANALYTICS_CONSOLE_REQUEST_FORBIDDEN");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void allowsSameOriginMutationWithConsoleRequestHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/assets/audience");
        request.addHeader(AnalyticsConsoleRequestGuardFilter.REQUEST_HEADER, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
