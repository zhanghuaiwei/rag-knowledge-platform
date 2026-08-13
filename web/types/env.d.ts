declare namespace NodeJS {
  interface ProcessEnv {
    readonly NEXT_PUBLIC_USE_MOCK?: "true" | "false";
    readonly NEXT_PUBLIC_API_BASE_URL?: string;
    readonly NEXT_PUBLIC_API_PREFIX?: string;
    readonly NEXT_PUBLIC_MINIO_BASE_URL?: string;
  }
}
