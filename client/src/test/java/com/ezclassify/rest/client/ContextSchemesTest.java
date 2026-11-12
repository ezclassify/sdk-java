package com.ezclassify.rest.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ezclassify.rest.contract.Assertion;
import com.ezclassify.rest.contract.AssertionConfidence;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The schemes of a Context: {@link ContextSchemes#get(String)} addresses a scheme the Context holds
 * by its key, sends that key as {@code ?scheme=} on every scheme operation, keeps its answers apart
 * from every other view's, and never lets the primary scheme answer for a key the server refuses.
 */
class ContextSchemesTest {
  private static final String PREFIX = "/v1/ctx/district/1.0.0";

  private static final String RESOLUTION =
      "\"resolution\":{\"contextId\":\"district\",\"resolvedVersion\":\"1.0.0\",\"artifacts\":[]}";
  private static final String PROCESS_ENVELOPE =
      "{"
          + RESOLUTION
          + ",\"result\":{\"assertions\":[],\"valid\":true,\"inputWasValid\":true,\"errors\":[],"
          + "\"warnings\":[],\"appliedFixes\":[],\"rendered\":\"H\",\"assessment\":{\"emitted\":[],"
          + "\"omitted\":[],\"collapsed\":[],\"conflicts\":[],\"unrepresentable\":[]},\"notices\":[],"
          + "\"proposedFixes\":[],\"renders\":{}}}";

  private static final Assertion LEVEL =
      new Assertion("level", "H", AssertionConfidence.CERTAIN, null, "user", null, null);

  /** One scheme operation: its route under the Context prefix and a call that sends it. */
  private record Operation(String route, Consumer<ContextScheme> call) {}

  private static final List<Operation> OPERATIONS =
      List.of(
          new Operation("scheme/process", s -> s.process("H")),
          new Operation("scheme/validate", s -> s.validate("H")),
          new Operation("scheme/validate", s -> s.validate(List.of(LEVEL))),
          new Operation("scheme/explain", s -> s.explain("H")),
          new Operation("scheme/suggest", s -> s.suggest("H")),
          new Operation(
              "scheme/suggest-for-categories",
              s -> s.suggestForCategories(List.of(LEVEL), List.of("level"))),
          new Operation("scheme/aggregate", s -> s.aggregate(List.of("H"))),
          new Operation("scheme/aggregate", s -> s.aggregateAssertions(List.of(List.of(LEVEL)))),
          new Operation("scheme/scan", s -> s.scan("text H text")),
          new Operation("scheme/render", s -> s.render(List.of(LEVEL))),
          new Operation("scheme/check-coverage", s -> s.checkCoverage(List.of("H"), "H")),
          new Operation("scheme/describe-concept", s -> s.describeConcept("H", "level")),
          new Operation("scheme/diff", s -> s.diff("H", "L")),
          new Operation("scheme/notices", s -> s.notices(List.of(LEVEL))),
          new Operation("scheme/view", ContextScheme::view),
          new Operation("scheme/apply-preset", s -> s.applyPreset("preset")),
          new Operation("scheme", ContextScheme::summary),
          new Operation("scheme/concepts", ContextScheme::concepts),
          new Operation("scheme/categories", ContextScheme::categories),
          new Operation("scheme/formats", ContextScheme::formats),
          new Operation("scheme/scopes", ContextScheme::scopes),
          new Operation("scheme/label-presets", ContextScheme::labelPresets));

  static Stream<Arguments> operations() {
    return OPERATIONS.stream().map(op -> Arguments.of(op.route(), op));
  }

  /** A stub that serves only the Context description: every operation answers 404. */
  private static ClientStubServer stub() throws Exception {
    Map<String, String> routes = new HashMap<>();
    routes.put(PREFIX, ClientStubServer.description(PREFIX));
    return new ClientStubServer(routes, "1.0.0");
  }

  private static EzClassifyContext context(ClientStubServer stub) {
    return new EzClassifyContext(stub.url(), "district", "1.0.0", null);
  }

  /** The request's query parameters as a set, so a route's own parameters keep their place. */
  private static Set<String> queryOf(ClientStubServer stub) {
    String query = stub.lastRequest().query();
    return query == null ? Set.of() : Arrays.stream(query.split("&")).collect(Collectors.toSet());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("operations")
  void aSchemeViewSendsItsSelectorOnEveryOperation(String route, Operation op) throws Exception {
    try (ClientStubServer stub = stub()) {
      ContextScheme view = context(stub).schemes().get("src/a");
      assertThrows(EzClassifyClientException.class, () -> op.call().accept(view), route);
      assertEquals(PREFIX + "/" + route, stub.lastRequest().path(), route);
      assertEquals(
          1,
          queryOf(stub).stream().filter(p -> p.startsWith("scheme=")).count(),
          route + " sends one scheme parameter");
      assertEquals(true, queryOf(stub).contains("scheme=src%2Fa"), route);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("operations")
  void thePrimaryViewSendsNoSelector(String route, Operation op) throws Exception {
    try (ClientStubServer stub = stub()) {
      ContextScheme primary = context(stub).scheme();
      assertThrows(EzClassifyClientException.class, () -> op.call().accept(primary), route);
      assertEquals(PREFIX + "/" + route, stub.lastRequest().path(), route);
      assertEquals(0, queryOf(stub).stream().filter(p -> p.startsWith("scheme=")).count(), route);
    }
  }

  @Test
  void aRouteThatCarriesItsOwnQueryKeepsItAndAddsTheSelector() throws Exception {
    try (ClientStubServer stub = stub()) {
      ContextScheme view = context(stub).schemes().get("target");
      assertThrows(EzClassifyClientException.class, () -> view.describeConcept("H", "level"));
      assertEquals(Set.of("conceptId=H", "categoryId=level", "scheme=target"), queryOf(stub));
    }
  }

  @Test
  void viewsAreHeldPerKey() throws Exception {
    try (ClientStubServer stub = stub()) {
      EzClassifyContext context = context(stub);
      assertSame(context.scheme(), context.scheme());
      assertSame(context.schemes(), context.schemes());
      assertSame(context.schemes().get("source"), context.schemes().get("source"));
      assertNotSame(context.schemes().get("source"), context.schemes().get("target"));
      assertNotSame(context.scheme(), context.schemes().get("source"));
      assertEquals(0, stub.requestCount(), "a view is a binding, not a request");
    }
  }

  @Test
  void aMissingOrBlankKeyIsRefusedBeforeAnyRequestIsSent() throws Exception {
    try (ClientStubServer stub = stub()) {
      EzClassifyContext context = context(stub);
      for (String key : new String[] {null, "", "  "}) {
        assertThrows(IllegalArgumentException.class, () -> context.schemes().get(key));
      }
      assertEquals(0, stub.requestCount());
    }
  }

  @Test
  void theServerIsTheOnlyAuthorityOnWhichKeysExist() throws Exception {
    try (ClientStubServer stub = stub()) {
      stub.setStatus(PREFIX + "/scheme/process", 404);
      stub.setResponse(
          PREFIX + "/scheme/process",
          "{\"error\":\"scheme not found\",\"code\":\"scheme_not_found\"}");

      // An artifact id is not a key. The client neither translates it nor refuses it from a list
      // of its own: it goes out as written, on the only request made, and the server answers.
      ContextScheme view =
          new EzClassifyContext(stub.url(), "district", "1.0.0", null).schemes().get("cvss@3.1.0");
      EzClassifyClientException refused =
          assertThrows(EzClassifyClientException.class, () -> view.process("H"));

      assertEquals(404, refused.statusCode());
      assertEquals("scheme_not_found", refused.code());
      assertEquals("scheme=cvss%403.1.0", stub.lastRequest().query());
      assertEquals(1, stub.requestCount(), "no description is read to vet the key");
    }
  }

  @Test
  void eachViewCachesItsOwnAnswers() throws Exception {
    try (ClientStubServer stub = stub()) {
      stub.setResponse(PREFIX + "/scheme/process", PROCESS_ENVELOPE);
      EzClassifyContext context = context(stub);

      context.scheme().process("H");
      assertNull(stub.lastRequest().query());
      context.schemes().get("source").process("H");
      assertEquals("scheme=source", stub.lastRequest().query());
      context.schemes().get("target").process("H");
      assertEquals("scheme=target", stub.lastRequest().query());
      assertEquals(3, stub.requestCount(), "one request per view for the same input");

      context.scheme().process("H");
      context.schemes().get("source").process("H");
      context.schemes().get("target").process("H");
      assertEquals(3, stub.requestCount(), "each view answers its repeat from its own entry");
    }
  }

  @Test
  void aKeyTheServerRefusesIsNeverAnsweredByThePrimaryScheme() throws Exception {
    try (ClientStubServer stub = stub()) {
      stub.setResponse(PREFIX + "/scheme/process", PROCESS_ENVELOPE);
      EzClassifyContext context = context(stub);
      context.scheme().process("H");
      assertEquals(1, stub.requestCount());

      stub.setStatus(PREFIX + "/scheme/process", 404);
      stub.setResponse(
          PREFIX + "/scheme/process", "{\"error\":\"scheme_not_found\",\"code\":\"NOT_FOUND\"}");
      EzClassifyClientException refused =
          assertThrows(
              EzClassifyClientException.class, () -> context.schemes().get("nope").process("H"));
      assertEquals(404, refused.statusCode());
      assertEquals("scheme=nope", stub.lastRequest().query());
      assertEquals(2, stub.requestCount());

      // The refusal is not kept, and it names a missing scheme, so the primary's entry stands.
      assertThrows(
          EzClassifyClientException.class, () -> context.schemes().get("nope").process("H"));
      assertEquals(3, stub.requestCount());
      context.scheme().process("H");
      assertEquals(3, stub.requestCount());
    }
  }
}
