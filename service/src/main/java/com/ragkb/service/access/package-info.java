/**
 * 统一授权域：KB 角色、文档 ACL、scope、分类许可、policyVersion、批量授权（PDP/PEP）。
 * 禁止在 repository 层把租户过滤当成唯一授权机制（03-详细设计 §3）。
 */
package com.ragkb.service.access;
