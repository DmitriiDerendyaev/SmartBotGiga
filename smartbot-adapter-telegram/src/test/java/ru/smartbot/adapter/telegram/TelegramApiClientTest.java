package ru.smartbot.adapter.telegram;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.smartbot.adapter.telegram.model.TelegramInlineResult;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@DisplayName("TelegramApiClient — контракт Bot API (WireMock)")
class TelegramApiClientTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stop() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("answerInlineQuery: is_personal=true, cache_time=0, token в callback_data")
    void answerInlineQueryContract() {
        wireMock.stubFor(post(urlPathEqualTo("/botTEST/answerInlineQuery"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        TelegramApiClient client = client();

        client.answerInlineQuery("iq-1", List.of(TelegramInlineResult.article(
                "r1", "SmartBot", "desc", "text", "Сгенерировать ответ", "g:token")));

        wireMock.verify(postRequestedFor(urlPathEqualTo("/botTEST/answerInlineQuery"))
                .withRequestBody(matchingJsonPath("$.is_personal", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.cache_time", equalTo("0")))
                .withRequestBody(matchingJsonPath("$.results[0].reply_markup.inline_keyboard[0][0].callback_data",
                        equalTo("g:token"))));
    }

    @Test
    @DisplayName("getUpdates: offset передаётся как query-параметр")
    void getUpdatesSendsOffset() {
        wireMock.stubFor(post(urlPathEqualTo("/botTEST/getUpdates"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":[{\"update_id\":42,\"inline_query\":"
                                + "{\"id\":\"iq\",\"from\":{\"id\":7},\"query\":\"hi\",\"offset\":\"\"}}]}")));

        var updates = client().getUpdates(41);

        org.assertj.core.api.Assertions.assertThat(updates).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(updates.get(0).updateId()).isEqualTo(42);
        var requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/botTEST/getUpdates")));
        org.assertj.core.api.Assertions.assertThat(requests).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(requests.get(0).getUrl()).contains("offset=41");
    }

    private static TelegramApiClient client() {
        TelegramProperties properties = new TelegramProperties();
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setPollTimeoutSeconds(1);
        WebClient webClient = new TelegramWebClientConfiguration()
                .telegramWebClient(properties, "TEST");
        return new TelegramApiClient(webClient, properties);
    }
}
