import { request } from "../../../services/http";
import { IntentNode, IntentNodePayload } from "../types";

export function fetchIntentNodes() {
  return request<IntentNode[]>("/intent-tree/query");
}

export function fetchIntentNodeDetail(id: string) {
  return request<IntentNode>(`/intent-tree/${encodeURIComponent(id)}`);
}

export function createIntentNode(params: IntentNodePayload) {
  return request<void>("/intent-tree", {
    method: "POST",
    body: JSON.stringify(params)
  });
}

export function updateIntentNode(recordId: string, params: IntentNodePayload) {
  return request<void>(`/intent-tree/${encodeURIComponent(recordId)}`, {
    method: "PUT",
    body: JSON.stringify(params)
  });
}

export function deleteIntentNode(recordId: string) {
  return request<void>(`/intent-tree/${encodeURIComponent(recordId)}`, {
    method: "DELETE"
  });
}
