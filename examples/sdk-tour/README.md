# EzClassify Java SDK Tour

`ClientDemo` walks the Java REST client through ping, validate, process, aggregate, suggest, and guide scan. It is part of the Maven reactor, and its smoke test runs against an in-JVM HTTP stub.

Run it against the free developer runtime. It is one container that serves the built-in Contexts, Cedar Valley among them, over real HTTP, with no license, credentials or account:

```sh
docker run --rm -p 3000:8080 ghcr.io/ezclassify/developer-runtime
```

Then run the tour from the repository root, in another terminal:

```sh
./mvnw install -DskipTests --batch-mode --no-transfer-progress
./mvnw -pl examples/sdk-tour exec:java \
    -Dexec.mainClass=com.ezclassify.demo.ClientDemo \
    -Dexec.args="http://localhost:3000 cedar-valley 1.0.0" \
    --batch-mode --no-transfer-progress
```

The first command installs the reactor artifacts into your local Maven repository so the example can resolve its sibling dependencies. The checked-in wrapper supplies Maven 3.10.0; Java 17+ and Node.js 22 or 24 are required to build. The tour itself needs only Java.

The arguments are the runtime's base URL, the Context id and its version. The tour's markings and guide text belong to Context `cedar-valley`, a fictional school district policy, so it runs as written against the developer runtime. `GET /v1/ctx` on the runtime lists every built-in Context.

To run against your own runtime, pass its base URL, Context id and version and substitute markings that Context recognizes:

```sh
./mvnw -pl examples/sdk-tour exec:java \
    -Dexec.mainClass=com.ezclassify.demo.ClientDemo \
    -Dexec.args="https://runtime.example.com my-context 1.0.0" \
    --batch-mode --no-transfer-progress
```

The tour builds an unauthenticated client, so use a test runtime that permits anonymous requests. For authenticated applications, see [swap to your runtime](../../README.md#swap-to-your-runtime) in the SDK quickstart.
