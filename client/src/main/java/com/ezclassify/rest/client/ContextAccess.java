package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.AccessDecision;
import com.ezclassify.rest.contract.AccessRequirementsDescription;
import com.ezclassify.rest.contract.AccessRuleDescription;
import com.ezclassify.rest.contract.Assertion;
import com.ezclassify.rest.contract.ContextAccessDecisionResult;
import com.ezclassify.rest.contract.ContextAccessRequirementsResult;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/** Access-control operations bound to one immutable server-resolved Context composition. */
public final class ContextAccess {
  private final EzClassifyContext ctx;

  ContextAccess(EzClassifyContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Checks immutable subject attributes, supplied as a map, against structured target assertions.
   *
   * <p>A host holding subject attributes as a map can supply them directly without serializing them
   * to JSON first.
   *
   * @param dataAssertions assertions describing the trusted target
   * @param userAttributes subject attributes
   * @return enriched access decision including activation, operands, comparisons, and provenance
   * @throws IllegalArgumentException if either argument is null
   */
  public ContextAccessDecisionResult check(
      List<Assertion> dataAssertions, Map<String, Object> userAttributes) {
    return check(dataAssertions, userAttributes, null);
  }

  /**
   * Checks immutable subject attributes, supplied as a map, against structured target assertions,
   * with contextual attributes for the evaluation.
   *
   * @param dataAssertions assertions describing the trusted target
   * @param userAttributes subject attributes
   * @param context contextual attributes, or {@code null} for none
   * @return enriched access decision including activation, operands, comparisons, and provenance
   * @throws IllegalArgumentException if {@code dataAssertions} or {@code userAttributes} is null
   */
  public ContextAccessDecisionResult check(
      List<Assertion> dataAssertions,
      Map<String, Object> userAttributes,
      Map<String, Object> context) {
    Args.requiredList(dataAssertions, "dataAssertions");
    Args.required(userAttributes, "userAttributes");
    JsonObject body = new JsonObject();
    Json.put(body, "assertions", ContextJson.assertionArray(dataAssertions));
    Json.put(body, "userAttributes", WireJson.toTree(userAttributes));
    if (context != null) Json.put(body, "context", WireJson.toTree(context));
    return decision(body);
  }

  /**
   * Checks immutable subject attributes, supplied as a map, against a trusted marking target.
   *
   * @param marking marking to normalize through the Context scheme
   * @param userAttributes subject attributes
   * @return enriched access decision including activation, operands, comparisons, and provenance
   * @throws IllegalArgumentException if either argument is null
   */
  public ContextAccessDecisionResult checkByMarking(
      String marking, Map<String, Object> userAttributes) {
    return checkByMarking(marking, userAttributes, null);
  }

  /**
   * Checks immutable subject attributes, supplied as a map, against a trusted marking target, with
   * contextual attributes for the evaluation.
   *
   * @param marking marking to normalize through the Context scheme
   * @param userAttributes subject attributes
   * @param context contextual attributes, or {@code null} for none
   * @return enriched access decision including activation, operands, comparisons, and provenance
   * @throws IllegalArgumentException if {@code marking} or {@code userAttributes} is null
   */
  public ContextAccessDecisionResult checkByMarking(
      String marking, Map<String, Object> userAttributes, Map<String, Object> context) {
    Args.required(marking, "marking");
    Args.required(userAttributes, "userAttributes");
    JsonObject body = new JsonObject();
    Json.put(body, "marking", marking);
    Json.put(body, "userAttributes", WireJson.toTree(userAttributes));
    if (context != null) Json.put(body, "context", WireJson.toTree(context));
    return decision(body);
  }

  /** Send a prepared access-check body and project the Context envelope. */
  private ContextAccessDecisionResult decision(JsonObject body) {
    return ContextJson.project(
        ctx.postResult(
            "access/check", body, value -> WireJson.fromTree(value, AccessDecision.class)),
        ContextAccessDecisionResult::new);
  }

  /**
   * Describes immutable-policy requirements for a marking target.
   *
   * @param marking trusted marking target
   * @return normalized target and qualified policy requirements
   */
  public ContextAccessRequirementsResult describeRequirements(String marking) {
    return describeRequirements(marking, null);
  }

  /**
   * Describes immutable-policy requirements for a structured assertion target.
   *
   * @param assertions trusted structured assertion target
   * @return normalized target and qualified policy requirements
   */
  public ContextAccessRequirementsResult describeRequirements(List<Assertion> assertions) {
    return describeRequirements(null, assertions);
  }

  /**
   * Describes immutable-policy requirements after merging marking and structured target forms.
   *
   * @param marking optional trusted marking target
   * @param assertions optional structured assertions merged with the marking
   * @return normalized target and qualified policy requirements
   */
  public ContextAccessRequirementsResult describeRequirements(
      String marking, List<Assertion> assertions) {
    if (marking == null && (assertions == null || assertions.isEmpty())) {
      throw new IllegalArgumentException("marking or assertions is required");
    }
    JsonObject body = new JsonObject();
    Json.put(body, "marking", marking);
    if (assertions != null) Json.put(body, "assertions", ContextJson.assertionArray(assertions));
    return ContextJson.project(
        ctx.postResult(
            "access/describe-requirements",
            body,
            value -> WireJson.fromTree(value, AccessRequirementsDescription.class)),
        ContextAccessRequirementsResult::new);
  }

  /**
   * Describe every rule in the context's access policy, in declared order.
   *
   * @return all qualified access rule descriptions
   */
  public List<AccessRuleDescription> rules() {
    return ctx.accessPolicy().rules();
  }

  /**
   * Return the access policy document's own overall marking, as the document writes it. It is
   * display text: nothing in the client or the server interprets it. It comes from the same
   * response as {@link #rules()}, so reading both takes one request.
   *
   * @return the policy's marking, or {@code null} when the policy declares none
   */
  public String marking() {
    return ctx.accessPolicy().marking();
  }

  /**
   * Return the ids of every rule in the context's access policy.
   *
   * <p>This derives the ids from {@link #rules()} so the SDK does not invent a second projection
   * endpoint.
   *
   * @return access rule ids in declared order
   */
  public List<String> ruleIds() {
    return rules().stream().map(rule -> rule.rule().ruleId()).toList();
  }
}
