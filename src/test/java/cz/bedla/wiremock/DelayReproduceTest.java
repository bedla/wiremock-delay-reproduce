package cz.bedla.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DelayReproduceTest {
    private WireMockServer wireMockServer;

    @BeforeAll
    void setUp() {
        wireMockServer = new WireMockServer(options()
                .dynamicPort()
                .dynamicHttpsPort() // <<--- un/comment this line
        );
        wireMockServer.start();
    }

    @AfterAll
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @Order(1)
    void timeout() throws IOException {
        wireMockServer.stubFor(get(urlEqualTo("/timeout"))
                .willReturn(aResponse()
                        .withFixedDelay(10 * 1000)
                        .withBody("body1")));

        downloadContentAndMeasure("/timeout", null);
    }

    @Test
    @Order(2)
    void content() throws IOException {
        wireMockServer.stubFor(get(urlEqualTo("/content"))
                .willReturn(aResponse()
                        .withBody("body2")));

        downloadContentAndMeasure("/content", "body2");
    }

    private void downloadContentAndMeasure(String urlDir, String expectedBody) throws IOException {
        final long start = System.currentTimeMillis();

        System.out.println(wireMockServer.baseUrl());
        try {
            final String url = "http://localhost:" + wireMockServer.port() + urlDir;
            final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6 * 1000);
            connection.setReadTimeout(3 * 1000);
            connection.setDoInput(true);
            if (expectedBody == null) {
                assertThrows(SocketTimeoutException.class, () -> httpGetContent(connection));
            } else {
                final String body = httpGetContent(connection);
                assertEquals(expectedBody, body);
            }
        } finally {
            System.out.println(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
        }
    }

    private String httpGetContent(HttpURLConnection connection) throws IOException {
        try (InputStream is = connection.getInputStream()) {
            return IOUtils.toString(is, Charsets.UTF_8);
        }
    }
}
