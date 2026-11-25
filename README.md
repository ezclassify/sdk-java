# <img src="assets/icon.svg" alt="" width="32"> EzClassify Java SDK

[![CI](https://github.com/ezclassify/sdk-java/actions/workflows/ci.yml/badge.svg)](https://github.com/ezclassify/sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.ezclassify/ezclassify-rest-client?label=com.ezclassify%3Aezclassify-rest-client)](https://central.sonatype.com/artifact/com.ezclassify/ezclassify-rest-client)
[![license: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/java-17%2B-orange.svg)](pom.xml)

This is the Java client library for the EzClassify REST API. Use it to parse and validate security markings and sensitivity labels, roll portion marks up into an overall marking, and check need-to-know access, from any Java 17+ application. Markings follow a scheme: IC Classification and Control Markings (CAPCO), Controlled Unclassified Information (CUI), Traffic Light Protocol (TLP) 2.0, Common Vulnerability Scoring System (CVSS), or rules you write yourself. The client needs a running EzClassify runtime; it does not contain the engine or any scheme content. The free developer runtime includes TLP 2.0, CVSS and demo subsets of CAPCO and CUI. The full CAPCO and CUI schemes are licensed content.

The [ezclassify/contracts](https://github.com/ezclassify/contracts) repository owns
document and wire types; EzClassify Runtime owns
marking and access evaluation. This client owns HTTP transport, response decoding, caching, and
language-native conveniences. Evaluation operations address a published Context;
health and Context discovery use the deployment endpoint.

## Install

```xml
<dependency>
  <groupId>com.ezclassify</groupId>
  <artifactId>ezclassify-rest-client</artifactId>
  <version>1.0.0</version>
</dependency>
```

Java 17+.

## Quickstart

Start with the free developer runtime. It is one container that serves the built-in Contexts, such as `capco-demo` and `cedar-valley`, over real HTTP, with no license, credentials or account. The client is plain Java 17+.

```sh
docker run --rm -p 3000:8080 ghcr.io/ezclassify/developer-runtime
```

Then connect with the normal client. Context `cedar-valley`, version `1.0.0`:

```java
import com.ezclassify.rest.client.EzClassifyClient;
import com.ezclassify.rest.client.EzClassifyContext;

public class QuickStart {
    public static void main(String[] args) {
        try (EzClassifyClient client = new EzClassifyClient("http://localhost:3000")) {
            EzClassifyContext context = client.context("cedar-valley", "1.0.0");
            var result = context.scheme().process("INTERNAL").result();
            System.out.println(result.rendered() + " valid=" + result.valid());
        }
    }
}
```

```text
INTERNAL valid=true
```

`cedar-valley` is a fictional school district policy for teaching, so use synthetic markings with it. `GET /v1/ctx` on the runtime lists every built-in Context.

### Validate, roll up and check access

The `capco-demo` Context on the developer runtime is a demo subset of CAPCO. It validates a marking, rolls several markings up into one overall marking, and checks access for a user. The guides and access rules in it are notional demo content.

```java
import com.ezclassify.rest.client.EzClassifyClient;
import com.ezclassify.rest.client.EzClassifyContext;
import java.util.List;
import java.util.Map;

public class CapcoDemo {
    public static void main(String[] args) {
        try (EzClassifyClient client = new EzClassifyClient("http://localhost:3000")) {
            EzClassifyContext context = client.context("capco-demo", "1.0.0");

            var read = context.scheme().validate("SECRET//NOFORN").result();
            System.out.println(read.rendered() + " valid=" + read.valid());

            var overall = context.scheme().aggregate(List.of(
                    "SECRET//REL TO USA, GBR, CAN",
                    "CONFIDENTIAL//REL TO USA, GBR",
                    "UNCLASSIFIED")).result();
            System.out.println(overall.rendered());

            var decision = context.access().checkByMarking(
                    "SECRET//NOFORN",
                    Map.of("clearance", "SECRET", "nationality", "GBR")).result();
            System.out.println("granted=" + decision.granted());
            for (var rule : decision.rules()) {
                if (!rule.skipped()) System.out.println("  " + rule.name() + ": " + rule.granted());
            }
        }
    }
}
```

```text
SECRET//NOFORN valid=true
SECRET//REL TO USA, GBR
granted=false
  Clearance level: true
  NOFORN: USA nationality claim required (demo): false
```

A SECRET-cleared user with a GBR nationality claim is denied a NOFORN document.

### Swap to your runtime

When the integration works, point the same code at your runtime. Four values change: the base URL, the Context, your application id and the caller token. Your runtime administrator supplies them, along with the Context token where your deployment requires one.

```java
import com.ezclassify.rest.client.EzClassifyClient;
import com.ezclassify.rest.client.EzClassifyContext;
import com.ezclassify.rest.client.RestClientCredentials;

var credentials = new RestClientCredentials(accessToken, contextToken, "your-application-id");
try (EzClassifyClient client = new EzClassifyClient("https://rest-api.example.com", credentials)) {
    System.out.println(client.listContexts());

    EzClassifyContext context = client.context("your-context", "latest");
    var result = context.scheme().validate("confidential").result();
    System.out.println(result.rendered() + " valid=" + result.valid());
    System.out.println(context.resolvedVersion());
}
```

The application id in that example is the id your application is registered under; the deployment counts its requests by it. It is not a credential and authorizes nothing, so it can travel with or without tokens:

```java
var credentials = new RestClientCredentials(userAccessToken, contextToken, "case-intake-portal");
```

The SDK sends the id on Context routes as `x-ezclassify-application-id`; health checks do not carry it. A request without it, or with an id the deployment has not registered, is served the same way. A supplied caller token travels independently as `Authorization: Bearer`. The id is 1 to 128 printable ASCII characters (0x21 to 0x7e, so no spaces); the SDK refuses any other id when the credentials are constructed. A value that does not match is served and counted as unidentified by the deployment. The SDK also redacts the tokens from diagnostics. The JDK transport does not follow redirects. Keep one client and its caches per caller; your application owns user sign-in and token refresh.

Use an `https://` endpoint anywhere the tokens leave the machine; a local development server on loopback may use `http://`. The client enforces this: supplying credentials for a plaintext `http://` endpoint on a non-loopback host is rejected at construction, as is a base URL that is not an absolute `http`/`https` URL.

## Contexts and operations

Pass a concrete version to pin the binding, or `"latest"` to track the newest one; the response carries the concrete resolved version. Omit the version to let the server choose: the Context token's default, or the newest published version. `client.context("your-context")` and `EzClassifyClient.connect(baseUrl, "your-context")` do that, and so does a `null` or empty version on the overloads that take one, which is what an unset configuration value usually is. Like `latest`, a binding without a version is unpinned. `context.scheme()`, `context.guide()`, `context.access()`, `context.crosswalk()`, and `context.document()` group Context-bound operations. The SDK deliberately exposes no flat scheme or registry routes: the Context selects the composed scheme, layers, guides, access policy, and crosswalks.

Scheme operations that read a marking have overloads that take their format choices under the names the wire uses. `inputFormat` says what the input is read as; pass `null` and recognition works it out. `outputFormat` picks the output the result is written in; pass `null` for the default output, which for a scheme that declares surfaces depends on the marking's role (see below). `surface` names which of the scheme's scopes the input is, for a fragment such as a portion; pass `null` and the input is treated as a whole marking. Each operation takes only the options its request declares, so `diff` takes just `inputFormat`, `suggestForCategories` and the facts overloads of `validate` take no `inputFormat`, and `render` and `applyPreset` only produce output, so they take a single `outputFormat`.

A scheme that declares surfaces names its outputs as `surface/view` addresses, such as `sensitivity/short`. An `inputFormat` may then name a view, by id or address, or one of the forms that view is read in, and `recognition().inputFormat()` reports the view the text was read as. A named `outputFormat` is written alone. With none, each surface the result writes comes back in its default for the marking's role, except that a default writing every other surface's scope beside its own comes back alone: the role in the request's `context` when it gives one, otherwise the one recognition decides and reports in `recognition().role()`. A part and a whole can therefore come back in different views.

Scheme operations take an immutable options object built with `ProcessOptions.builder()`, `ValidateOptions.builder()`, `ExplainOptions.builder()`, `SuggestOptions.builder()`, `AggregateOptions.builder()`, `ScanOptions.builder()`, or `RenderOptions.builder()`. The one-argument calls use defaults. Facts use `FactsValidateOptions`, `FactsSuggestOptions`, and `FactsAggregateOptions`, which have no text input-format setter. Suggestions take text or facts, exactly one; typed text together with chosen facts belongs in `process` with `suggestFor`.

A `null` option is left off the request. An empty list and `Boolean.FALSE` are sent, so an explicit choice reaches the server. `parts` names the part markings the whole must cover. On `process`, every rank floor is worked out against the whole and those parts together, and every proposed fix is proven with them. Context role and whole-marking facts stay in the generated `ProcessingContext` contract.

## Reading a marking and writing one

Two operations answer about one marking, and they are split by who is asking.

`validate` is the reader's call. Code that displays a marking, gates on it, stores it, or hands its facts to an access check calls it, with the text or with the facts a document states:

```java
ContextScheme scheme = context.scheme();

ValidateResult read = scheme.validate("confidential").result();
if (!read.valid()) {
    read.errors().forEach(error -> System.out.println(error.message()));
}
```

`valid` says the marking stands once the server has settled it. `inputWasValid` says it was right as written; when it is false, `appliedFixes` and `errors` say why. Pass facts to `validate(List<Assertion>)` only as a document or an author stated them. The `assertions` of an earlier result are already settled, and validating those can call a marking valid whose text was not.

`process` is the author's call. It answers everything `validate` does, settled the same way, plus what someone writing the marking needs: the corrections to the text, the fix paths the server has proven, a rendering for each output you name, and picker options for each category you name. A composer sends what was typed together with the facts already picked:

```java
ProcessResult draft =
    scheme.process(
        typed,
        ProcessOptions.builder()
            .assertions(pickedSoFar)
            .outputs(List.of("short"))
            .suggestFor(List.of("level"))
            .build())
        .result();

String preview = draft.renders().get("short").rendered();
List<Suggestion> options = draft.suggestionsFor().get("level").suggestions();
```

`renders` holds only the outputs you name, and `suggestionsFor` is there only when you ask for it. Outputs are spelled the way the scheme declares them: a format id here, a `surface/view` address for a scheme that declares surfaces. An error about a token the server could not read may carry `didYouMean()`: when present, it names the concepts the token may have been meant as, so a host can offer them without parsing the message. Its absence says nothing about whether the token was stray text, so a missing hint is never a reason to ignore the token.

`explain` takes what `process` takes, apart from `outputs` and `suggestFor`, and returns the trace instead: what was read, repaired, inferred and found wrong, and what was written.

```java
ExplanationTrace why = scheme.explain("confidential").result();
why.entries().forEach(step -> System.out.println(step.stage() + ": " + step.detail()));
```

`suggestForCategories` answers several pickers against one committed assertion set in one round trip, keyed by category id, each as if that category's own choice were not made yet. If the same edit also processes the marking, name the categories in `suggestFor` on that `process` call instead and the same map comes back in `suggestionsFor`.

## Reading the scheme itself

Six reads answer what the composed scheme declares, rather than operating on a marking. They return the contract's view types directly, with no `result` envelope:

| Call | Returns |
|------|---------|
| `scheme().summary()` | `SchemeSummary`: id, version, name, default scope and render formats, display vocabulary, color rules |
| `scheme().concepts()` | `List<ConceptView>` |
| `scheme().categories()` | `List<CategoryView>`, in declared order |
| `scheme().formats()` | `List<FormatView>`: each format's id, surface, layout and syntax |
| `scheme().scopes()` | `List<SchemeScope>`, in declaration order, empty when none are declared |
| `scheme().labelPresets()` | `List<LabelPresetView>`: id, label, description and scope, empty when none are declared |

```java
ContextScheme scheme = context.scheme();

for (SchemeScope scope : scheme.scopes()) {
    System.out.println(scope.id() + ": " + scope.label());
}
for (FormatView format : scheme.formats()) {
    System.out.println(format.id() + " writes " + format.surface() + " in " + format.mode().toWire() + " mode");
}
```

A preset is listed by name and scope only; `applyPreset` is the way to expand one.

### Another scheme in the Context

A Context can hold more than one scheme, such as the two sides of a crosswalk. `context.scheme()` is the primary scheme. `context.schemes().get(key)` returns the view of any scheme the Context holds, named by its Context-local key; `context.describeContext().schemes()` lists those keys as each descriptor's `selector()`. The view has every operation and read `scheme()` has, and none of them take a key: the view sends it as `?scheme=` on every request.

```java
ContextScheme source = context.schemes().get("source");

var checked = source.validate("MARKING");
List<CategoryView> categories = source.categories();
```

The server decides which keys exist. A key the Context does not hold is a 404 `scheme_not_found` on the first call, and it is never answered by the primary scheme. `schemes()` keeps one view per key, and each view's answers are cached on their own.

## Response caching

A Context handle answers a repeated identical question from memory. These operations are cached: `process`, `validate`, `explain`, `render`, `diff`, `aggregate`, `aggregateAssertions`, `applyPreset`, `suggest`, `suggestForCategories`, `scan`, `describeConcept`, `access().rules()`, `access().marking()`, and `describeCrosswalks()`. Every other operation reaches the server on every call.

- **Keys.** The scheme view and every field of the request are part of the key, so two `process` calls that name different `outputs` or `suggestFor` are two entries, and `explain` never answers from a `process` entry for the same input. Member order and members left unset do not change the key.
- **Concurrent calls.** Identical calls made while one is in flight share that one HTTP request. A request that runs longer than its lifetime stays joinable.
- **Failures.** A refused request and a 200 whose body is missing a required member are never kept. The next call goes back to the server.
- **Results.** Every call gets its own freshly decoded result, so changing one never affects another caller or a later cached read.
- **Lifetime.** A concrete Context version is immutable, so its entries stay until LRU eviction or `context.clearCache()`. A `latest` binding, and one that names no version, keeps entries for five minutes, two minutes for scans, and one hour for concept descriptions and the access-rule list.
- **Invalidation.** A response that names a different resolved version than the one the entries were answered under clears them. So does a 401 and the runtime's constant `{"error":"not_found"}` refusal, whatever credential the handle holds. A 404 that names a missing concept or scheme is an answer about that resource and leaves the cache alone. A handle belongs to one caller identity; build a new one when the caller changes.

Access checks reach the server on every call so each decision appears in its audit trail. Document and guide operations, `access().describeRequirements`, and `crosswalk().translate` are never cached, and neither are the [scheme reads](#reading-the-scheme-itself): `summary`, `concepts`, `categories`, `formats`, `scopes`, and `labelPresets` ask the server every time.

When supplying cache options or credentials, keep the version argument: `client.context("my-context", null, options)` or `EzClassifyClient.connect(baseUrl, "my-context", null, credentials)`. A `null` version lets the server choose while keeping these overloads unambiguous.

Use `new ContextCacheOptions(true)` to disable response caching for a handle. The `clearCache()` method drops retained operation answers without dropping the Context binding or its description.

### Recording what answered

`describeContext()` returns what the Context is made of: its scheme and layers, guides, access policy, and crosswalks, each with the version the Context declares and the concrete version that answered. Store those versions with a decision to show later which artifacts produced it.

```java
CtxDescribeResponseBody description = context.describeContext();
for (CtxComponentDescriptor crosswalk : description.crosswalks()) {
    System.out.println(crosswalk.id() + " " + crosswalk.version() + " resolved to " + crosswalk.resolvedVersion());
}
```

The description is read once when the handle connects and held, not cached with the operation answers. `clearCache()` leaves it in place. Under `latest`, or without a version, it is read again the first time you ask after a response reports a different resolved version.

## Modules

The SDK ships as one artifact, `com.ezclassify:ezclassify-rest-client`. It holds the HTTP client in
`com.ezclassify.rest.client` and the generated wire types in `com.ezclassify.rest.contract`.
The Javadoc reference covers both packages. Internal transport helpers and repository tools
are outside that public API.



The SDK is a client only. Engine operation belongs to EzClassify's licensed runtime host.

## Versioning

SDK `major.minor` tracks the REST API's `major.minor`; patch versions may differ.
`client.engineSpecVersion()` reports this client's contract version, while
`client.ping()` reads the deployment's version and build identity. A major/minor
mismatch logs a one-time warning through `java.util.logging`. Mixed minor
versions are unsupported.

## Demo

[`examples/sdk-tour/`](examples/sdk-tour/) walks ping, validate, process, aggregate, suggest and guide scan. It runs against the developer runtime from the [quickstart](#quickstart). Start the runtime, then install the reactor artifacts locally and run the example module:

```sh
./mvnw install -DskipTests --batch-mode --no-transfer-progress
./mvnw -pl examples/sdk-tour exec:java \
    -Dexec.mainClass=com.ezclassify.demo.ClientDemo \
    -Dexec.args="http://localhost:3000 cedar-valley 1.0.0" \
    --batch-mode --no-transfer-progress
```

To run it against your runtime, pass its base URL, Context id and version in `-Dexec.args` the same way and substitute markings that Context recognizes. The tour builds an unauthenticated client, so use a test runtime that permits anonymous requests.

## Errors and rate limits

A request the caller builds wrongly, such as a missing required argument, fails before it is sent with `IllegalArgumentException`. A success response that is not what the contract promises, because it is not JSON or lacks a member the contract requires, raises `MalformedResponseException`, a subclass of `EzClassifyClientException` whose `path()` names the missing member, such as `$.result.errors[0].message`. Such an answer is never cached; the next call asks the server again.

A response with a status of 400 or above raises `EzClassifyClientException`. `statusCode()` is the HTTP status, `code()` and `detail()` carry the server's stable error code and sanitized detail when it sent them, and `getMessage()` is the public error text. Classify on `code()`, not on the message. The server refuses a request with a query parameter or body field its operation does not declare, so a request the client builds never carries one; a 400 means the request is wrong, not that it is worth sending again.

A 429 means the server's rate limit answered. `retryAfter()` is a `Duration`: the `Retry-After` header when the response has one, otherwise the `retryAfter` seconds in the body, and `null` when the server gave neither. Any other error returns `null`. The client never waits or retries for you, so decide whether and when to send the request again:

```java
try {
    context.scheme().validate(marking);
} catch (EzClassifyClientException error) {
    if (error.statusCode() == 429 && error.retryAfter() != null) {
        Thread.sleep(error.retryAfter().toMillis());
        // send the request again, or give up after a bound you choose
    } else {
        throw error;
    }
}
```

## Limits

The REST client accepts at most 32 MiB of response-body bytes per request, across all client and Context operations and for both success and error responses. It rejects an oversized body with `EzClassifyClientException` as a local transport failure (`statusCode() == 0`), even if the server sent an HTTP status; this fixed limit has no per-request override.

## Timeouts

Every call ends within a fixed deadline: 30 seconds for a Context operation, including the description `connect` reads, and 5 seconds for `ping`, `listContexts` and `listContextVersions`. The deadline covers the whole exchange, from opening the connection to the last byte of the response body, so a server that sends its headers and then stalls cannot hold the caller. When it passes, the client closes the connection and throws `EzClassifyClientException` with `statusCode() == 0` and a `java.net.http.HttpTimeoutException` as its cause. The client never retries.

Interrupting the calling thread ends the call right away. The exchange is cancelled, the thread's interrupt flag stays set, and the exception's cause is the `InterruptedException`, so a timeout and a cancellation are told apart by the cause. A caller waiting on a cached answer another thread is already fetching stops waiting as well, and that fetch carries on for the others. For a tighter bound on one call, run it on an executor and cancel the `Future` when your own deadline passes:

```java
Future<?> call = executor.submit(() -> context.scheme().validate(marking));
try {
    call.get(2, TimeUnit.SECONDS);
} catch (TimeoutException tooSlow) {
    call.cancel(true); // interrupts the call, which ends its exchange
}
```

## Integration testing

The developer runtime from the [quickstart](#quickstart) also evaluates access decisions. Use it to check connectivity, response handling, and access-decision logging with your normal client.

```java
import com.ezclassify.rest.client.EzClassifyClient;
import com.ezclassify.rest.client.EzClassifyClientException;
import java.util.Map;

try (var client = new EzClassifyClient("http://localhost:3000")) {
    var ctx = client.context("cedar-valley", "1.0.0");
    var decision = ctx.access().checkByMarking("CONFIDENTIAL", Map.of()).result();
    System.out.println("granted=" + decision.granted());
    for (var rule : decision.rules()) {
        System.out.println(rule.rule().ruleId() + " " + rule.granted() + " " + rule.reason());
    }
} catch (EzClassifyClientException error) {
    System.err.println("Integration request failed: " + error.statusCode() + " " + error.getMessage());
}
```

The example logs the outcome and rule explanations without copying subject
attributes or credentials into the log. In your backend, derive attributes
from trusted identity data and add your application's correlation ID and
resource reference. Apply your log retention and redaction policy to returned
explanations too. A denied access decision is a successful response with
`granted: false`; handle transport or authentication failures separately.

To rehearse transport failures such as a 429, a dropped connection or a malformed body, run the Apache-2.0 [conformance server](https://github.com/ezclassify/contracts/tree/main/packages/conformance-server#testing-an-application-integration) (Node.js 22 or 24). It replays recorded failures on demand, which the developer runtime does not produce. Clear the client response cache or create a new client between runs so an earlier answer does not hide the scenario.

## Reporting issues

File product issues at [github.com/ezclassify/ezclassify](https://github.com/ezclassify/ezclassify). Report vulnerabilities through the [security policy](https://github.com/ezclassify/ezclassify/blob/main/SECURITY.md).

## License

[Apache License 2.0](LICENSE) © EzClassify, LLC. Contributions are accepted under the [Developer Certificate of Origin](https://github.com/ezclassify/ezclassify/blob/main/DCO.md): sign off your commits with `git commit -s`.
