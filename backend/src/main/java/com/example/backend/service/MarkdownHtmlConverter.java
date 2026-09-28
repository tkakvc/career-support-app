package com.example.backend.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

// 【学習ポイント：押さえておく】OpenAIに直接HTMLを生成させず、Markdownを生成させてから
// このクラスでHTMLに変換する。LLMは複雑なHTML（閉じタグの対応等）よりMarkdownの方が
// 構文を崩さずに出力しやすいため、生成失敗率が下がる（docs/ai/api/overview.md参照）。
@Component
public class MarkdownHtmlConverter {

    private static final String STYLE = """
            <style>
              .ai-reference { font-family: sans-serif; line-height: 1.7; color: #1a1a1a; }
              .ai-reference h1, .ai-reference h2, .ai-reference h3 { margin-top: 1.2em; }
              .ai-reference pre { background: #f5f5f5; padding: 12px; overflow-x: auto; border-radius: 6px; }
              .ai-reference code { background: #f5f5f5; padding: 2px 4px; border-radius: 4px; }
              .ai-reference a { color: #2563eb; }
            </style>
            """;

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String toHtml(String markdown) {
        Node document = parser.parse(markdown);
        String body = renderer.render(document);
        return STYLE + "<div class=\"ai-reference\">\n" + body + "</div>";
    }
}
