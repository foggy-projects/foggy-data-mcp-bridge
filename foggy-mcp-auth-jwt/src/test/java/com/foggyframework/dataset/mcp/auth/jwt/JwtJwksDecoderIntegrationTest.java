package com.foggyframework.dataset.mcp.auth.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtJwksDecoderIntegrationTest {

    private HttpServer server;
    private final AtomicReference<String> jwks = new AtomicReference<>();
    private String issuer;
    private String jwkSetUri;

    @BeforeEach
    void startJwksServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] body = jwks.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        jwkSetUri = issuer + "/jwks";
    }

    @AfterEach
    void stopJwksServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validatesSignatureIssuerAudienceExpiryAndJwksRotation() throws Exception {
        RSAKey first = new RSAKeyGenerator(2048).keyID("key-1").generate();
        RSAKey second = new RSAKeyGenerator(2048).keyID("key-2").generate();
        jwks.set(jwks(first));
        JwtDecoder decoder = decoder();

        assertThat(decoder.decode(token(first, JWSAlgorithm.RS256, issuer, "foggy-data",
                Instant.now().plusSeconds(300))).getSubject()).isEqualTo("user-1");

        jwks.set(jwks(second));
        assertThat(decoder.decode(token(second, JWSAlgorithm.RS256, issuer, "foggy-data",
                Instant.now().plusSeconds(300))).getSubject()).isEqualTo("user-1");

        assertThatThrownBy(() -> decoder.decode(token(second, JWSAlgorithm.RS256,
                "http://wrong-issuer", "foggy-data", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(second, JWSAlgorithm.RS256,
                issuer, "wrong-audience", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(second, JWSAlgorithm.RS256,
                issuer, "foggy-data", Instant.now().minusSeconds(120))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(second, JWSAlgorithm.RS512,
                issuer, "foggy-data", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class);
    }

    private JwtDecoder decoder() {
        JwtMcpAuthProperties properties = new JwtMcpAuthProperties();
        properties.setIssuerUri(issuer);
        properties.setJwkSetUri(jwkSetUri);
        properties.setAudiences(List.of("foggy-data"));
        properties.setAllowedAlgorithms(Set.of("RS256"));
        properties.setClockSkew(Duration.ofSeconds(30));
        properties.setAllowInsecureHttp(true);
        return new JwtMcpAuthAutoConfiguration().foggyMcpJwtDecoder(properties);
    }

    private static String jwks(RSAKey key) {
        return "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}";
    }

    private static String token(
            RSAKey key,
            JWSAlgorithm algorithm,
            String issuer,
            String audience,
            Instant expiresAt) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(expiresAt))
                .claim("tenant_id", "tenant-1")
                .claim("roles", List.of("ANALYST"))
                .claim("scope", "mcp:read")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(algorithm)
                        .type(JOSEObjectType.JWT)
                        .keyID(key.getKeyID())
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }
}