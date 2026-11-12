package com.ezclassify.rest.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The application id header, its validation and redirect behavior over actual loopback HTTP. */
class ApplicationIdTest {
  private static final String APP_ID = "case-intake-portal";
  private static final String HEADER = "x-ezclassify-application-id";
  private static final String CONTEXT = "/v1/ctx/sample/1.0.0";

  private static RestClientCredentials application() {
    return new RestClientCredentials(null, null, APP_ID);
  }

  private static String header(ClientStubServer stub, String name) {
    return stub.lastRequest().headers().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(entry -> String.join(",", entry.getValue()))
        .findFirst()
        .orElse(null);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        " ",
        " leading",
        "trailing ",
        "inner space",
        "tab\tvalue",
        "line\r\nvalue",
        "zero\0value",
        "delete\u007fvalue",
        "non-ascii\u00e9"
      })
  void malformedApplicationIdsRefuse(String value) {
    assertThrows(
        IllegalArgumentException.class, () -> new RestClientCredentials(null, null, value));
  }

  @Test
  void applicationIdGrammarBoundsLengthAndCharacters() {
    var longest = "a".repeat(128);
    assertEquals(longest, new RestClientCredentials(null, null, longest).applicationId());
    assertEquals("!~", new RestClientCredentials(null, null, "!~").applicationId());
    var tooLong =
        assertThrows(
            IllegalArgumentException.class,
            () -> new RestClientCredentials(null, null, "a".repeat(129)));
    assertTrue(tooLong.getMessage().contains("1 to 128 characters"));
  }

  @Test
  void applicationIdAccompaniesCallerAndContextTokens() {
    var credentials = new RestClientCredentials("caller", "context", APP_ID);
    assertEquals(APP_ID, credentials.applicationId());
    assertTrue(credentials.toString().contains("applicationId=" + APP_ID));
  }

  @Test
  void callerBearerAndApplicationIdRemainIndependentAcrossNativeClients() throws Exception {
    var path = CONTEXT + "/scheme/validate";
    var body =
        "{\"resolution\":{\"contextId\":\"sample\",\"resolvedVersion\":\"1.0.0\",\"artifacts\":[]},\"result\":{\"assertions\":[],\"valid\":true,\"inputWasValid\":true,\"errors\":[],\"warnings\":[],\"appliedFixes\":[],\"rendered\":\"TEXT\",\"assessment\":{\"emitted\":[],\"omitted\":[],\"collapsed\":[],\"conflicts\":[],\"unrepresentable\":[]},\"notices\":[]}}";
    try (var stub =
            new ClientStubServer(
                Map.of(
                    CONTEXT,
                    ClientStubServer.description(CONTEXT),
                    path,
                    body,
                    "/v1/health",
                    ClientStubServer.health("1.0.0", "abc")),
                "1.0.0");
        var first =
            new EzClassifyClient(
                stub.url(), new RestClientCredentials("first-user", null, APP_ID));
        var second =
            new EzClassifyClient(
                stub.url(), new RestClientCredentials("second-user", null, APP_ID))) {
      var firstContext = first.context("sample", "1.0.0");
      assertEquals("Bearer first-user", header(stub, "Authorization"));
      firstContext.scheme().validate("text");
      assertEquals("Bearer first-user", header(stub, "Authorization"));
      var secondContext = second.context("sample", "1.0.0");
      assertEquals("Bearer second-user", header(stub, "Authorization"));
      secondContext.scheme().validate("text");
      assertEquals("Bearer second-user", header(stub, "Authorization"));
      assertEquals(APP_ID, header(stub, HEADER));
      assertNull(header(stub, "X-EzClassify-Context-Token"));
      firstContext.scheme().validate("text");
      assertEquals(4, stub.requestCount());
      first.ping();
      assertEquals("Bearer first-user", header(stub, "Authorization"));
      assertNull(header(stub, HEADER));
      assertNull(header(stub, "X-EzClassify-Context-Token"));
    }
  }

  @Test
  void applicationIdAloneDoesNotTriggerThePlaintextGuard() {
    // The id is not a secret, so it travels over plain http the way an unauthenticated request
    // does.
    new EzClassifyClient("http://remote.example", application()).close();
  }

  @Test
  void applicationIdReachesContextRoutesButNotHealth() throws Exception {
    try (var stub =
            new ClientStubServer(
                Map.of(
                    "/v1/health",
                    ClientStubServer.health("1.0.0", "abc"),
                    "/v1/ctx",
                    "{\"contexts\":[\"sample\"]}",
                    "/v1/ctx/sample",
                    "{\"id\":\"sample\",\"versions\":[\"1.0.0\"]}",
                    CONTEXT,
                    ClientStubServer.description(CONTEXT),
                    CONTEXT + "/scheme/validate",
                    "{\"resolution\":{\"contextId\":\"sample\",\"resolvedVersion\":\"1.0.0\",\"artifacts\":[]},\"result\":{\"assertions\":[],\"valid\":true,\"inputWasValid\":true,\"errors\":[],\"warnings\":[],\"appliedFixes\":[],\"rendered\":\"TEXT\",\"assessment\":{\"emitted\":[],\"omitted\":[],\"collapsed\":[],\"conflicts\":[],\"unrepresentable\":[]},\"notices\":[]}}"),
                "1.0.0");
        var root = new EzClassifyClient(stub.url(), application())) {
      root.ping();
      assertNull(header(stub, HEADER));
      assertEquals(List.of("sample"), root.listContexts());
      assertCredential(stub);
      assertEquals(List.of("1.0.0"), root.listContextVersions("sample"));
      assertCredential(stub);
      var bound = root.context("sample", "1.0.0");
      assertCredential(stub);
      assertEquals("TEXT", bound.scheme().validate("text").result().rendered());
      assertEquals("POST", stub.lastRequest().method());
      assertCredential(stub);
      var connected = EzClassifyClient.connect(stub.url(), "sample", "1.0.0", application());
      assertEquals("sample", connected.contextId());
      assertCredential(stub);
    }
  }

  @Test
  void requestsWithoutAnApplicationIdCarryNoHeader() throws Exception {
    try (var stub =
            new ClientStubServer(
                Map.of("/v1/health", ClientStubServer.health("1.0.0", "abc")), "1.0.0");
        var root = new EzClassifyClient(stub.url())) {
      root.ping();
      assertNull(header(stub, HEADER));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {".", ".."})
  void dotSegmentsRefuseBeforeTransport(String segment) throws Exception {
    try (var stub = new ClientStubServer(Map.of());
        var root = new EzClassifyClient(stub.url(), application())) {
      assertThrows(IllegalArgumentException.class, () -> root.listContextVersions(segment));
      assertThrows(IllegalArgumentException.class, () -> root.context(segment, "1.0.0"));
      assertThrows(IllegalArgumentException.class, () -> root.context("sample", segment));
      assertThrows(
          IllegalArgumentException.class,
          () -> EzClassifyClient.connect(stub.url(), segment, "1.0.0", application()));
      assertThrows(
          IllegalArgumentException.class,
          () -> EzClassifyClient.connect(stub.url(), "sample", segment, application()));
      assertEquals(0, stub.requestCount());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void forbiddenPostIsNeverRefreshedOrReplayed(boolean factory) throws Exception {
    try (var stub =
            new ClientStubServer(
                Map.of(
                    CONTEXT,
                    ClientStubServer.description(CONTEXT),
                    CONTEXT + "/scheme/validate",
                    "{\"code\":\"ACCESS_DENIED\",\"detail\":\"refused\"}"),
                "1.0.0");
        var root = new EzClassifyClient(stub.url(), application())) {
      stub.setStatus(CONTEXT + "/scheme/validate", 403);
      var bound =
          factory
              ? EzClassifyClient.connect(stub.url(), "sample", "1.0.0", application())
              : root.context("sample", "1.0.0");
      var error =
          assertThrows(EzClassifyClientException.class, () -> bound.scheme().validate("text"));
      assertEquals(403, error.statusCode());
      assertEquals(2, stub.requestCount());
      assertEquals("POST", stub.lastRequest().method());
      assertCredential(stub);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "true,true,false",
    "true,false,false",
    "false,true,false",
    "false,false,false",
    "true,true,true",
    "true,false,true",
    "false,true,true",
    "false,false,true"
  })
  void sameAndCrossOriginRedirectsAreNotFollowed(boolean factory, boolean sameOrigin, boolean user)
      throws Exception {
    var credentials = new RestClientCredentials(user ? "caller" : null, null, APP_ID);
    try (var target = new ClientStubServer(Map.of(CONTEXT, ClientStubServer.description(CONTEXT)));
        var source = new ClientStubServer(Map.of(CONTEXT, "{}", "/v1/ctx", "{}"));
        var root = new EzClassifyClient(source.url(), credentials)) {
      var path = factory ? CONTEXT : "/v1/ctx";
      source.setStatus(path, 302);
      source.setHeader(path, "Location", (sameOrigin ? source.url() : target.url()) + CONTEXT);
      var failure =
          assertThrows(
              EzClassifyClientException.class,
              () -> {
                if (factory) EzClassifyClient.connect(source.url(), "sample", "1.0.0", credentials);
                else root.listContexts();
              });
      assertEquals(302, failure.statusCode());
      assertEquals(1, source.requestCount());
      assertEquals(0, target.requestCount());
      assertEquals(APP_ID, header(source, HEADER));
      assertEquals(user ? "Bearer caller" : null, header(source, "Authorization"));
      assertNull(header(source, "X-EzClassify-Context-Token"));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void redirectWithValidOperationBodyIsRefusedAndNeverCached(boolean applicationMode)
      throws Exception {
    var path = CONTEXT + "/scheme/validate";
    var body =
        "{\"resolution\":{\"contextId\":\"sample\",\"resolvedVersion\":\"1.0.0\",\"artifacts\":[]},\"result\":{\"assertions\":[],\"valid\":true,\"inputWasValid\":true,\"errors\":[],\"warnings\":[],\"appliedFixes\":[],\"rendered\":\"TEXT\",\"assessment\":{\"emitted\":[],\"omitted\":[],\"collapsed\":[],\"conflicts\":[],\"unrepresentable\":[]},\"notices\":[]}}";
    try (var stub =
            new ClientStubServer(
                Map.of(CONTEXT, ClientStubServer.description(CONTEXT), path, body), "1.0.0");
        var root =
            new EzClassifyClient(
                stub.url(), applicationMode ? application() : RestClientCredentials.none())) {
      var bound = root.context("sample", "1.0.0");
      stub.setStatus(path, 302);
      stub.setHeader(path, "Location", stub.url() + path);
      var failure =
          assertThrows(EzClassifyClientException.class, () -> bound.scheme().validate("text"));
      assertEquals(302, failure.statusCode());
      assertEquals(2, stub.requestCount());
      stub.setStatus(path, 200);
      stub.setResponse(path, body.replace("TEXT", "FRESH"));
      assertEquals("FRESH", bound.scheme().validate("text").result().rendered());
      assertEquals(3, stub.requestCount());
    }
  }

  private static void assertCredential(ClientStubServer stub) {
    assertEquals(APP_ID, header(stub, HEADER));
    assertNull(header(stub, "Authorization"));
    assertNull(header(stub, "X-EzClassify-Context-Token"));
  }
}
