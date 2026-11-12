package com.ezclassify.rest.client;

import java.net.http.HttpRequest;

/**
 * Caller credentials and the calling application's id.
 *
 * <p>The tokens are secrets; the application id is not. It names the application for the
 * deployment's per-application usage reporting, authorizes nothing, and is sent on every request as
 * the {@code x-ezclassify-application-id} header. A request without it, or with an id the
 * deployment has not registered, is served the same way.
 *
 * @param accessToken bearer token identifying the caller, or {@code null} when authentication is
 *     not required by the application policy
 * @param contextToken signed token authorizing Context operations, or {@code null} when not
 *     required by the Runtime's configured authorization policy
 * @param applicationId the id this application is registered under, or {@code null}; nonempty
 *     printable ASCII without surrounding whitespace
 */
public record RestClientCredentials(String accessToken, String contextToken, String applicationId) {

  private static final String MALFORMED_APPLICATION_ID =
      "Application id must be nonempty printable ASCII without surrounding whitespace";

  /**
   * Create independent caller and Context credentials with no application id.
   *
   * @param accessToken bearer token identifying the caller, or {@code null}
   * @param contextToken signed Context token, or {@code null}
   * @throws IllegalArgumentException when a supplied token is blank
   */
  public RestClientCredentials(String accessToken, String contextToken) {
    this(accessToken, contextToken, null);
  }

  /**
   * Reject malformed credentials before any HTTP transport is used.
   *
   * @param accessToken bearer token identifying the caller, or {@code null}
   * @param contextToken signed Context authorization token, or {@code null}
   * @param applicationId the application's registered id, or {@code null}
   * @throws IllegalArgumentException when a token is blank or the application id is malformed
   */
  public RestClientCredentials {
    if (applicationId != null) {
      if (applicationId.isEmpty()
          || applicationId.charAt(0) == ' '
          || applicationId.charAt(applicationId.length() - 1) == ' ') {
        throw new IllegalArgumentException(MALFORMED_APPLICATION_ID);
      }
      for (int i = 0; i < applicationId.length(); i++) {
        char character = applicationId.charAt(i);
        if (character < 0x20 || character > 0x7e) {
          throw new IllegalArgumentException(MALFORMED_APPLICATION_ID);
        }
      }
    }
    if (accessToken != null && accessToken.isBlank()) {
      throw new IllegalArgumentException("accessToken must be null or non-blank");
    }
    if (contextToken != null && contextToken.isBlank()) {
      throw new IllegalArgumentException("contextToken must be null or non-blank");
    }
  }

  /**
   * Return credentials that add no request headers.
   *
   * @return credentials that add no headers
   */
  public static RestClientCredentials none() {
    return new RestClientCredentials(null, null);
  }

  /**
   * Render the credential shape without the secrets. The token values are never included, so a host
   * that logs this record or interpolates it into a diagnostic cannot leak them. The application id
   * is not a secret and is shown.
   *
   * @return the presence of each token and the application id
   */
  @Override
  public final String toString() {
    return "RestClientCredentials[accessToken="
        + present(accessToken)
        + ", contextToken="
        + present(contextToken)
        + ", applicationId="
        + (applicationId == null ? "none" : applicationId)
        + "]";
  }

  private static String present(String token) {
    return token == null ? "none" : "set";
  }

  /** Whether any token will be put on the wire; drives the plaintext-transport check. */
  boolean present() {
    return accessToken != null || contextToken != null;
  }

  /**
   * Apply the caller token, the Context token on Context routes, and the application id.
   *
   * @param request request builder to receive the headers
   * @param contextPath whether this request addresses the Context route family
   */
  void apply(HttpRequest.Builder request, boolean contextPath) {
    if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
    if (contextPath && contextToken != null) {
      request.header("x-ezclassify-context-token", contextToken);
    }
    if (applicationId != null) request.header("x-ezclassify-application-id", applicationId);
  }
}
