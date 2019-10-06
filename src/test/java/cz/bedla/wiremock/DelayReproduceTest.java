package cz.bedla.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(DelayReproduceTest.class);

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

    @BeforeEach
    void reset() {
        log.info("reset");
        wireMockServer.resetAll();
    }

    @Test
    @Order(1)
    void timeout() throws IOException {
        log.info("test timeout start");

        wireMockServer.stubFor(get(urlEqualTo("/timeout"))
                .willReturn(aResponse()
                        .withFixedDelay(10 * 1000)
                        .withBody("body1")));

        downloadContentAndMeasure("/timeout", null);

        log.info("test timeout end");
    }

    @Test
    @Order(2)
    void content() throws IOException {
        log.info("test content start");

        wireMockServer.stubFor(get(urlEqualTo("/content"))
                .willReturn(aResponse()
                        .withBody("body2")));
        log.info("test content stub");

        downloadContentAndMeasure("/content", "body2");

        log.info("test content end");
    }

    // HTTPS tests here

    private void downloadContentAndMeasure(String urlDir, String expectedBody) throws IOException {
        log.info("downloadContentAndMeasure urlDir={}", urlDir);

        final long start = System.currentTimeMillis();


        boolean exceptionOccurred = false;
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
        } catch (Exception e) {
            exceptionOccurred = true;
            log.info("exception '{}' after ms {}", e.getMessage(), TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
            throw e;
        } finally {
            if (!exceptionOccurred) {
                log.info("downloaded at ms {}", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - start));
            }
        }
    }

    private String httpGetContent(HttpURLConnection connection) throws IOException {
        try (InputStream is = connection.getInputStream()) {
            return IOUtils.toString(is, Charsets.UTF_8);
        }
    }
}
