import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";

import Home from "./page";

describe("Home 页面", () => {
  it("渲染平台标题", () => {
    const html = renderToStaticMarkup(<Home />);
    expect(html).toContain("RAG 知识库平台");
  });
});
