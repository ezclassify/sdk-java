package com.ezclassify.rest.client;

import com.ezclassify.rest.contract.ContextGuideEvaluationResult;
import com.ezclassify.rest.contract.ContextGuideScanResult;
import com.ezclassify.rest.contract.ContextGuideViewResult;
import com.ezclassify.rest.contract.EvaluationResult;
import com.ezclassify.rest.contract.GuideRuleSelection;
import com.ezclassify.rest.contract.GuideScanResult;
import com.ezclassify.rest.contract.GuideViewResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Guide-domain operations bound to an {@link EzClassifyContext}. Calls route through {@code
 * /v1/ctx/{ctxId}/{version}/guide/*}; bodies carry payload only.
 */
public final class ContextGuide {

  private final EzClassifyContext ctx;

  ContextGuide(EzClassifyContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Scan free text for guide rule matches across the context's guides.
   *
   * @param text the free text to scan
   * @return one result per matching rule, with the matched keywords and spans
   * @throws IllegalArgumentException if a required argument is null
   */
  public ContextGuideScanResult scan(String text) {
    return scan(text, null);
  }

  /**
   * Scan free text for guide rule matches, narrowed to one guide.
   *
   * @param text the free text to scan
   * @param guideId the guide to scan within, or {@code null} for all guides
   * @return one result per matching rule, with the matched keywords and spans
   * @throws IllegalArgumentException if a required argument is null
   */
  public ContextGuideScanResult scan(String text, String guideId) {
    Args.required(text, "text");
    JsonObject body = new JsonObject();
    Json.put(body, "text", text);
    if (guideId != null) Json.put(body, "guideId", guideId);
    return ContextJson.project(
        ctx.postResult(
            "guide/scan", body, value -> WireJson.listFromTree(value, GuideScanResult.class)),
        ContextGuideScanResult::new);
  }

  /**
   * Evaluate a guide with the given rule selection.
   *
   * @param guideId the guide to evaluate
   * @param ruleIds the ids of the rules to select for the evaluation
   * @return the evaluation result, with the aggregated marking and the rules that were selected and
   *     computed as active
   * @throws IllegalArgumentException if a required argument is null or empty
   */
  public ContextGuideEvaluationResult evaluate(String guideId, String... ruleIds) {
    Args.requiredId(guideId, "guideId");
    Args.required(ruleIds, "ruleIds");
    JsonObject body = new JsonObject();
    Json.put(body, "guideId", guideId);
    Json.put(body, "selectedRuleIds", Json.stringArray(ruleIds));
    return ContextJson.project(
        ctx.postResult("guide/evaluate", body, value -> evaluationResult(value.getAsJsonObject())),
        ContextGuideEvaluationResult::new);
  }

  /**
   * Roll up rules selected from several guides. Each selection names the guide that holds its rule,
   * so two guides that use the same rule id hold two rules. Each guide evaluates the rules selected
   * from it, with its own dependencies and overrides, and the outcomes are rolled up per target
   * scheme.
   *
   * @param selections the selected rules, each with its guide
   * @return the evaluation result, with the combined marking and contributing rules
   * @throws IllegalArgumentException if the array or a selection is null, or a selection's guide or
   *     rule id is null or empty
   */
  public ContextGuideEvaluationResult evaluateMany(GuideRuleSelection... selections) {
    Args.required(selections, "selections");
    JsonArray wire = new JsonArray();
    for (GuideRuleSelection selection : selections) {
      Args.required(selection, "selection");
      JsonObject entry = new JsonObject();
      Json.put(entry, "guideId", Args.requiredId(selection.guideId(), "guideId"));
      Json.put(entry, "ruleId", Args.requiredId(selection.ruleId(), "ruleId"));
      wire.add(entry);
    }
    JsonObject body = new JsonObject();
    Json.put(body, "selections", wire);
    return ContextJson.project(
        ctx.postResult("guide/evaluate", body, value -> evaluationResult(value.getAsJsonObject())),
        ContextGuideEvaluationResult::new);
  }

  /**
   * Build a render-ready structural view of the context's first guide.
   *
   * @return the structural view, with the guide's rules grouped by category
   */
  public ContextGuideViewResult view() {
    return view(null);
  }

  /**
   * Build a render-ready structural view of a specific guide.
   *
   * @param guideId the guide to view, or {@code null} for the context's first guide
   * @return the structural view, with the guide's rules grouped by category
   */
  public ContextGuideViewResult view(String guideId) {
    JsonObject body = new JsonObject();
    if (guideId != null) Json.put(body, "guideId", guideId);
    return ContextJson.project(
        ctx.postResult(
            "guide/view", body, value -> WireJson.fromTree(value, GuideViewResponse.class)),
        ContextGuideViewResult::new);
  }

  private static EvaluationResult evaluationResult(JsonObject v) {
    return WireJson.fromTree(v, EvaluationResult.class);
  }
}
