package com.foggyframework.dataset.mcp.chart;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EChartsRenderer 单元测试")
class EChartsRendererTest {

    private WireMockServer wireMock;
    private EChartsRenderer renderer;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        renderer = new EChartsRenderer(WebClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .defaultHeader("Authorization", "Bearer configured-service-token")
                .build());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void shouldForwardNativeOptionAndInjectDatasetSource() {
        byte[] image = new byte[]{1, 2, 3, 4};
        wireMock.stubFor(post(urlEqualTo("/render/native/stream"))
                .willReturn(aResponse().withStatus(200).withBody(image)));

        ChartRenderResult result = renderer.render(new ChartRenderRequest(
                Map.of(
                        "title", Map.of("text", "分类销售额"),
                        "xAxis", Map.of("type", "category"),
                        "yAxis", Map.of("type", "value"),
                        "series", List.of(Map.of(
                                "type", "bar",
                                "encode", Map.of("x", "category", "y", "sales")
                        ))
                ),
                List.of(
                        Map.of("category", "A", "sales", 10),
                        Map.of("category", "B", "sales", 20)
                ),
                new ChartImageSpec(900, 500, "png"),
                "trace-echarts"
        ));

        assertEquals("bar", result.chartType());
        assertEquals("分类销售额", result.title());
        assertEquals(4, result.bytes().length);

        wireMock.verify(postRequestedFor(urlEqualTo("/render/native/stream"))
                .withHeader("Authorization", equalTo("Bearer configured-service-token"))
                .withHeader("X-Request-Id", equalTo("trace-echarts"))
                .withRequestBody(equalToJson("""
                        {
                          "engine": "echarts",
                          "engine_spec": {
                            "title": {"text": "分类销售额"},
                            "xAxis": {"type": "category"},
                            "yAxis": {"type": "value"},
                            "series": [
                              {
                                "type": "bar",
                                "encode": {"x": "category", "y": "sales"}
                              }
                            ],
                            "dataset": {
                              "source": [
                                {"category": "A", "sales": 10},
                                {"category": "B", "sales": 20}
                              ]
                            }
                          },
                          "image": {"format": "png", "width": 900, "height": 500}
                        }
                        """, true, true)));
    }

    @Test
    void shouldRejectDatasetArrayWhenInjectingQueryRows() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of(
                                "dataset", List.of(Map.of("id", "source")),
                                "series", List.of(Map.of("type", "bar"))
                        ),
                        List.of(Map.of("category", "A", "sales", 10)),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );

        assertTrue(error.getMessage().contains("dataset 数组"));
    }

    @Test
    void shouldRejectEmbeddedDatasetSource() {
        assertRejectedOption(
                Map.of(
                        "dataset", Map.of("source", List.of(Map.of("x", 1))),
                        "series", List.of(Map.of("type", "bar"))
                ),
                "dataset.source"
        );
    }

    @Test
    void shouldRejectEmbeddedSeriesData() {
        assertRejectedOption(
                Map.of(
                        "xAxis", Map.of("type", "category"),
                        "series", List.of(Map.of(
                                "type", "bar",
                                "data", List.of(1, 2)
                        ))
                ),
                "series[*].data"
        );
    }

    @Test
    void shouldRejectEmbeddedXAxisData() {
        assertRejectedOption(
                Map.of(
                        "xAxis", Map.of(
                                "type", "category",
                                "data", List.of("A", "B")
                        ),
                        "series", List.of(Map.of("type", "bar"))
                ),
                "xAxis.data"
        );
    }

    @Test
    void shouldRejectDatasetTransformAndChains() {
        assertRejectedOption(
                Map.of(
                        "dataset", Map.of(
                                "transform", Map.of("type", "filter")
                        ),
                        "series", List.of(Map.of("type", "bar"))
                ),
                "transform"
        );
        assertRejectedOption(
                Map.of(
                        "dataset", Map.of("fromDatasetIndex", 0),
                        "series", List.of(Map.of("type", "bar"))
                ),
                "dataset 链"
        );
    }

    @Test
    void shouldRejectUnsupportedImageFormat() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        Map.of("series", Map.of("type", "bar")),
                        List.of(Map.of("category", "A", "sales", 10)),
                        new ChartImageSpec(800, 600, "jpg"),
                        "trace"
                ))
        );
        assertTrue(error.getMessage().contains("png/svg"));
    }

    private void assertRejectedOption(
            Map<String, Object> option,
            String messagePart
    ) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(new ChartRenderRequest(
                        option,
                        List.of(Map.of("category", "A", "sales", 10)),
                        new ChartImageSpec(800, 600, "png"),
                        "trace"
                ))
        );
        assertTrue(error.getMessage().contains(messagePart));
    }
}
