package com.foggyframework.dataset.mcp.auth.jwt;

import com.foggyframework.dataset.mcp.auth.McpAccessTokenVerifier;
import com.foggyframework.dataset.model.spi.SecurityIdentityResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@AutoConfiguration
@ConditionalOnClass({JwtDecoder.class, McpAccessTokenVerifier.class})
@Conditional(OAuthResourceServerModeCondition.class)
@ConditionalOnMissingBean(McpAccessTokenVerifier.class)
@EnableConfigurationProperties(JwtMcpAuthProperties.class)
public class JwtMcpAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder foggyMcpJwtDecoder(JwtMcpAuthProperties properties) {
        validateDecoderProperties(properties);
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = StringUtils.hasText(properties.getJwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                : NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri());
        Set<SignatureAlgorithm> allowedAlgorithms = signatureAlgorithms(properties.getAllowedAlgorithms());
        builder.jwsAlgorithms(algorithms -> {
            algorithms.clear();
            algorithms.addAll(allowedAlgorithms);
        });
        NimbusJwtDecoder decoder = builder.build();

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator(properties.getClockSkew()));
        if (StringUtils.hasText(properties.getIssuerUri())) {
            validators.add(new JwtIssuerValidator(properties.getIssuerUri()));
        }
        validators.add(audienceValidator(properties.getAudiences()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    JwtMcpAccessTokenVerifier jwtMcpAccessTokenVerifier(
            JwtDecoder jwtDecoder,
            JwtMcpAuthProperties properties) {
        validateCommonProperties(properties);
        return new JwtMcpAccessTokenVerifier(jwtDecoder, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityIdentityResolver.class)
    SecurityIdentityResolver jwtSecurityIdentityResolver(JwtMcpAccessTokenVerifier verifier) {
        return new JwtSecurityIdentityResolver(verifier);
    }

    private static void validateDecoderProperties(JwtMcpAuthProperties properties) {
        validateCommonProperties(properties);
        if (!StringUtils.hasText(properties.getIssuerUri())
                && !StringUtils.hasText(properties.getJwkSetUri())) {
            throw new IllegalStateException(
                    "foggy.auth.jwt.issuer-uri or jwk-set-uri is required");
        }
        validateUri(properties.getIssuerUri(), "issuer-uri", properties.isAllowInsecureHttp());
        validateUri(properties.getJwkSetUri(), "jwk-set-uri", properties.isAllowInsecureHttp());
        if (properties.getAllowedAlgorithms() == null || properties.getAllowedAlgorithms().isEmpty()) {
            throw new IllegalStateException("foggy.auth.jwt.allowed-algorithms must not be empty");
        }
        signatureAlgorithms(properties.getAllowedAlgorithms());
    }

    private static void validateCommonProperties(JwtMcpAuthProperties properties) {
        if (properties.getAudiences() == null || properties.getAudiences().stream().noneMatch(StringUtils::hasText)) {
            throw new IllegalStateException("foggy.auth.jwt.audiences must not be empty");
        }
        Duration clockSkew = properties.getClockSkew();
        if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException(
                    "foggy.auth.jwt.clock-skew must be between 0 and 10 minutes");
        }
        if (!StringUtils.hasText(properties.getSubjectClaim())) {
            throw new IllegalStateException("foggy.auth.jwt.subject-claim must not be empty");
        }
    }

    private static void validateUri(String value, String propertyName, boolean allowInsecureHttp) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("foggy.auth.jwt." + propertyName + " must be an absolute URI", invalid);
        }
        if (!uri.isAbsolute() || uri.getRawAuthority() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(
                    "foggy.auth.jwt." + propertyName + " must be absolute without query or fragment");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !(allowInsecureHttp && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException(
                    "foggy.auth.jwt." + propertyName + " must use HTTPS");
        }
    }

    private static Set<SignatureAlgorithm> signatureAlgorithms(Set<String> configured) {
        Set<SignatureAlgorithm> algorithms = new LinkedHashSet<>();
        for (String name : configured) {
            SignatureAlgorithm algorithm = SignatureAlgorithm.from(name == null ? "" : name.trim());
            if (algorithm == null) {
                throw new IllegalStateException("Unsupported JWT signature algorithm: " + name);
            }
            algorithms.add(algorithm);
        }
        return algorithms;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(List<String> configuredAudiences) {
        Set<String> expected = new LinkedHashSet<>();
        configuredAudiences.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(expected::add);
        return jwt -> jwt.getAudience().stream().anyMatch(expected::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "JWT audience is not allowed", null));
    }
}

final class OAuthResourceServerModeCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(
            ConditionContext context,
            AnnotatedTypeMetadata metadata) {
        String configured = context.getEnvironment().getProperty("foggy.auth.mode", "AUTO");
        String normalized = configured == null
                ? "AUTO"
                : configured.trim().replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        return "OAUTH_RESOURCE_SERVER".equals(normalized)
                ? ConditionOutcome.match("foggy.auth.mode is " + configured)
                : ConditionOutcome.noMatch("foggy.auth.mode is not OAuth resource-server mode");
    }
}