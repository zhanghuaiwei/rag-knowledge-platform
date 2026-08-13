/**
 * Browser-visible runtime configuration.
 *
 * Next.js replaces NEXT_PUBLIC_* values at build time, so every variable must be
 * referenced statically. These values are public endpoints only; credentials and
 * object-storage secrets must stay in the backend configuration.
 */

const DEFAULT_API_BASE_URL = "http://localhost:8080";
const DEFAULT_API_PREFIX = "/api/v1";
const DEFAULT_MINIO_BASE_URL = "http://localhost:9000";

function normalizeBaseUrl(value: string | undefined, fallback: string): string {
  return (value?.trim() || fallback).replace(/\/+$/, "");
}

function normalizePathPrefix(value: string | undefined, fallback: string): string {
  const path = value?.trim() || fallback;
  return `/${path.replace(/^\/+|\/+$/g, "")}`;
}

function joinUrl(baseUrl: string, path: string): string {
  return `${baseUrl}/${path.replace(/^\/+/, "")}`;
}

const apiBaseUrl = normalizeBaseUrl(process.env.NEXT_PUBLIC_API_BASE_URL, DEFAULT_API_BASE_URL);
const apiPrefix = normalizePathPrefix(process.env.NEXT_PUBLIC_API_PREFIX, DEFAULT_API_PREFIX);
const minioBaseUrl = normalizeBaseUrl(process.env.NEXT_PUBLIC_MINIO_BASE_URL, DEFAULT_MINIO_BASE_URL);

export const publicEnv = Object.freeze({
  useMock: process.env.NEXT_PUBLIC_USE_MOCK !== "false",
  apiBaseUrl,
  apiPrefix,
  apiUrl: `${apiBaseUrl}${apiPrefix}`,
  minioBaseUrl,
});

/** Build an absolute backend API URL for redirects, SSE, and other non-Axios calls. */
export function buildApiUrl(path: string): string {
  return joinUrl(publicEnv.apiUrl, path);
}

/** Build a public MinIO/object URL from a bucket/object path. */
export function buildMinioUrl(path: string): string {
  return joinUrl(publicEnv.minioBaseUrl, path);
}
