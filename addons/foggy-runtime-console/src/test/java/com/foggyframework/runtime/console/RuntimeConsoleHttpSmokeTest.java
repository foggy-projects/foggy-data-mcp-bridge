package com.foggyframework.runtime.console;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
        "foggy.runtime-console.enabled=true",
        "foggy.runtime-api.enabled=true",
        "foggy.runtime-api.security-mode=auth-code",
        "foggy.runtime-api.auth-code=dummy-console-smoke-secret",
        "foggy.runtime-api.auth-scope=management-all"
})
@Import(RuntimeConsoleAutoConfiguration.class)
class RuntimeConsoleHttpSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Test
    void consoleEntryRedirectsToTrailingSlash() throws Exception {
        mockMvc.perform(get("/console"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/console/"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void trailingSlashForwardsToPackagedIndexWithSecurityHeaders() throws Exception {
        mockMvc.perform(get("/console/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/console/index.html"))
                .andExpect(header().string("Content-Security-Policy",
                        RuntimeConsoleSecurityHeadersFilter.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
