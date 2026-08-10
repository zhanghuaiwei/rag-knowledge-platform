"use client";

import { useEffect, useState } from "react";

import { api } from "@/api-client";
import type { Kb, KnowledgeHealth } from "@/api-client";

/**
 * 脚手架演示页：通过 api-client 消费内置 mock 数据，验证 mock 层可用。
 * 业务页面待 OpenAPI v0.2 冻结后按 features/ 目录实现。
 */
export default function Home() {
  const [kbs, setKbs] = useState<Kb[] | null>(null);
  const [health, setHealth] = useState<KnowledgeHealth | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const [kbPage, healthData] = await Promise.all([
          api.listKbs({ page: 1, size: 50 }),
          api.getKnowledgeHealth(),
        ]);
        if (!cancelled) {
          setKbs(kbPage.items);
          setHealth(healthData);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "加载失败");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: 24, fontFamily: "system-ui" }}>
      <h1>RAG 知识库平台</h1>
      <p>
        脚手架阶段：以下数据来自内置 <code>api-client + mocks</code> 层，可脱离后端完整演示。
      </p>

      {error ? <p style={{ color: "#b42318" }}>加载失败：{error}</p> : null}

      {health ? (
        <p>
          知识库健康度：无答案率 {(health.noAnswerRate * 100).toFixed(1)}% · 低置信率{" "}
          {(health.lowConfRate * 100).toFixed(1)}% · 平均置信 {health.averageConfidence.toFixed(2)} ·
          新鲜度 {health.freshnessScore.toFixed(2)}
        </p>
      ) : null}

      <h2>知识库列表（mock）</h2>
      {kbs === null ? (
        <p>加载中…</p>
      ) : kbs.length === 0 ? (
        <p>暂无知识库</p>
      ) : (
        <ul>
          {kbs.map((kb) => (
            <li key={kb.id}>
              {kb.name} — {kb.documentCount} 文档 / {kb.chunkCount} 分块 · 我的角色 {kb.role}
              {kb.status !== "ACTIVE" ? `（${kb.status}）` : ""}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
