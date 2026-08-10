/**
 * 摄取域：解析、OCR、结构化提取、分块、embedding、幂等补偿（编排，重计算落在 rag-engine/worker）。
 * 领域变更与 outbox_event 同事务提交（02-概要设计 §4.2）。
 */
package com.ragkb.service.ingestion;
