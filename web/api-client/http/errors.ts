/**
 * API 统一错误类型。
 *
 * 后端错误信封：`{ code, message, requestId, timestamp }`；HTTP 状态与 code 一一映射
 * （见 service ErrorCode）。前端按 code 做差异化处理（07-API契约 §7）。
 */
export class ApiError extends Error {
  /** HTTP 状态码；网络层错误时为 undefined。 */
  readonly status: number | undefined;
  /** 后端业务错误码（如 E-1003）；网络错误时为 E-NET 系列。 */
  readonly code: string;
  /** 后端请求追踪号，可用于反馈排查。 */
  readonly requestId: string | undefined;

  constructor(
    message: string,
    options: { status?: number; code?: string; requestId?: string } = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.status = options.status;
    this.code = options.code ?? (options.status != null ? `E-${options.status}` : "E-NET");
    this.requestId = options.requestId;
  }

  /** 401 未认证（会话过期）。 */
  get isUnauthorized(): boolean {
    return this.status === 401 || this.code === "E-1001";
  }

  /** 403 无权限。 */
  get isForbidden(): boolean {
    return this.status === 403 || this.code === "E-1002";
  }
}
