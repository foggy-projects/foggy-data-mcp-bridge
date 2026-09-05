package com.foggyframework.dataset.model.semantic.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.support.QueryInputValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Handles only Query DSL input failures on the native semantic query endpoints. */
@RestControllerAdvice(assignableTypes = {
        NativeDatasetController.class,
        SemanticServiceV3TestController.class
})
@ConditionalOnProperty(
        prefix = "foggy.dataset.exception-handler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SemanticQueryInputExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(SemanticQueryInputExceptionHandler.class);

    @ExceptionHandler(QueryInputValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RX<?> handleQueryInputValidationException(QueryInputValidationException ex) {
        logger.warn("Query DSL input rejected: code={}, violations={}",
                ex.getCode(), ex.getViolations().size());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", ex.getCode());
        details.put("violations", ex.getViolations());
        return RX.builder()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .error(details)
                .build();
    }
}
