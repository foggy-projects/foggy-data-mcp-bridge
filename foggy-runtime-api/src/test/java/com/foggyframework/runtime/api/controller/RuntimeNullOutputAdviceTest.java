package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.runtime.api.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeNullOutputAdviceTest {
    final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    final RuntimeNullOutputAdvice advice = new RuntimeNullOutputAdvice(mapper);

    Object encode(Object body, String mode) {
        var request = new MockHttpServletRequest();
        if (mode != null) request.addHeader(RuntimeNullOutputAdvice.HEADER, mode);
        return advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class, new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()));
    }

    @Test void explicitNullsRetainRowsWithoutMaterializingAbsentOrUnauthorizedFields() throws Exception {
        Map<String,Object> row = new LinkedHashMap<>(); row.put("ratio", null); row.put("average", null);
        var query = new SemanticQueryResponse(); query.setItems(List.of(row));
        var compose = new ComposeResponse(true, "compose", "execute", Map.of("plans", List.of(row)), null,
                List.of(), List.of(), Map.of());
        for (Object data : List.of(query, compose)) {
            var envelope = RuntimeEnvelope.ok("java", "v1", data);
            String normal = mapper.writeValueAsString(encode(envelope, null));
            assertThat(normal).doesNotContain("\"ratio\":null");
            String explicit = mapper.writeValueAsString(encode(envelope, "explicit"));
            assertThat(explicit).contains("\"ratio\":null", "\"average\":null")
                    .doesNotContain("secret", "\"error\":null", "\"sql\":null");
            assertThat(row).containsEntry("ratio", null).hasSize(2);
            assertThat(mapper.writeValueAsString(envelope)).isEqualTo(normal);
        }
        query.setItems(List.of());
        assertThat(mapper.writeValueAsString(encode(RuntimeEnvelope.ok("java", "v1", query), "explicit")))
                .contains("\"items\":[]");
    }

    @Test void partialNullAndInvalidModesAreUnambiguous() throws Exception {
        Map<String,Object> row = new LinkedHashMap<>(); row.put("id", 1); row.put("nullable", null);
        var query = new SemanticQueryResponse(); query.setItems(List.of(row));
        var body = RuntimeEnvelope.ok("java", "v1", query);
        assertThat(mapper.writeValueAsString(encode(body, "explicit"))).contains("\"id\":1", "\"nullable\":null");
        assertThat(encode(body, "omit")).isSameAs(body);
        assertThatThrownBy(() -> encode(body, "zero")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
