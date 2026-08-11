/** 通用分页与主体类型。 */

export interface PageParams {
  page?: number;
  size?: number;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
}

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  tenantId: number;
  tenantName: string;
  roles: string[];
  orgName: string;
}
