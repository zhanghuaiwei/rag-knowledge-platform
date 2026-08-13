import { buildApiUrl, buildMinioUrl } from "@/config/env";

/** Enterprise SSO entry point; kept in the API layer so pages do not assemble protocol URLs. */
export function getSsoAuthorizeUrl(): string {
  return buildApiUrl("/auth/authorize");
}

/** Resolve a bucket/object path when an API returns an object path instead of a presigned URL. */
export function getObjectStorageUrl(objectPath: string): string {
  return buildMinioUrl(objectPath);
}
