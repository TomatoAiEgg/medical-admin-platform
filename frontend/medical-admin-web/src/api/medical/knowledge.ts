import type { KnowledgeChunk, KnowledgeDocument, KnowledgeImportPayload, KnowledgeNamespaceSummary, KnowledgeRetrievalPayload, MetadataRevisionPayload, PageParams } from './types';
import { get, post, put } from '@/utils/request';

export function listKnowledgeDocuments(params?: PageParams) {
  return get<KnowledgeDocument[]>('/medical/knowledge/document/list', params).json();
}

export function listKnowledgeNamespaceSummary() {
  return get<KnowledgeNamespaceSummary[]>('/medical/knowledge/document/namespace/summary').json();
}

export function listKnowledgeChunks(params?: PageParams) {
  return get<KnowledgeChunk[]>('/medical/knowledge/chunk/list', params).json();
}

export function listCleanTasks(params?: PageParams) {
  return get('/medical/knowledge/clean/task/list', params).json();
}

export function listVectorTasks(params?: PageParams) {
  return get('/medical/knowledge/vector/task/list', params).json();
}

export function listMetadataRevisions(params?: PageParams) {
  return get('/medical/knowledge/metadata/revision/list', params).json();
}

export function listQualityChecks(params?: PageParams) {
  return get('/medical/knowledge/quality/list', params).json();
}

export function listRetrievalLogs(params?: PageParams) {
  return get('/medical/knowledge/retrieval/log/list', params).json();
}

export function checkKnowledgeQuality() {
  return post('/medical/knowledge/quality/check', {}).json();
}

export function importKnowledgeDocument(data: KnowledgeImportPayload) {
  return post('/medical/knowledge/document/import', data).json();
}

export function runKnowledgeClean(data: { documentId: string; cleanStrategy?: string }) {
  return post('/medical/knowledge/clean/run', data).json();
}

export function rebuildKnowledgeVector(data: { documentId: string; embeddingModel?: string }) {
  return post('/medical/knowledge/vector/rebuild', data).json();
}

export function reviseKnowledgeMetadata(data: MetadataRevisionPayload) {
  return put('/medical/knowledge/metadata/revision', data).json();
}

export function testKnowledgeRetrieval(data: KnowledgeRetrievalPayload) {
  return post('/medical/knowledge/retrieval/test', data).json();
}
