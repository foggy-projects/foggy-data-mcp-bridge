package com.foggyframework.runtime.api.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Per-response transport option; never mutates the engine result or shared mapper. */
@RestControllerAdvice(assignableTypes = {RuntimeQueryController.class, RuntimeComposeController.class})
public class RuntimeNullOutputAdvice implements ResponseBodyAdvice<Object> {
    public static final String HEADER = "X-Foggy-Null-Output";
    private final ObjectMapper mapper;
    private final ObjectMapper explicitMapper;

    public RuntimeNullOutputAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
        this.explicitMapper = mapper.copy().setDefaultPropertyInclusion(
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
    }

    @Override
    public boolean supports(MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
        return org.springframework.http.converter.json.MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter parameter, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().add("Vary", HEADER);
        String mode = request.getHeaders().getFirst(HEADER);
        if (mode == null || "omit".equalsIgnoreCase(mode.trim())) return body;
        if (!"explicit".equalsIgnoreCase(mode.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    HEADER + " must be omit or explicit");
        }
        if (body instanceof RuntimeEnvelope<?> envelope && envelope.success()) {
            ObjectNode tree = mapper.valueToTree(body);
            tree.set("data", explicitMapper.valueToTree(envelope.data()));
            return tree;
        }
        return body;
    }
}
