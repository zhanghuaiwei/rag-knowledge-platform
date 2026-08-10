/**
 * 应用层：use cases、事务边界、授权编排、幂等。
 * 领域变更与 outbox 同事务提交；禁止在事务内等待模型/解析/对象上传（03-详细设计 §11.1）。
 */
package com.ragkb.service.application;
