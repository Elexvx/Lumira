package com.lumira.saas.modules.system.workorder.app;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.regex.Pattern;

final class WorkOrderHtmlSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "p", "br", "div", "span",
                    "strong", "b", "em", "i", "u", "s",
                    "ul", "ol", "li", "blockquote", "pre", "code",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "a", "img"
            )
            .allowAttributes("href", "title").onElements("a")
            .allowAttributes("src", "alt", "title", "loading", "width", "height").onElements("img")
            .allowStandardUrlProtocols()
            .requireRelNofollowOnLinks()
            .toFactory();
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern IMAGE = Pattern.compile("(?i)<img\\b");

    private WorkOrderHtmlSanitizer() {
    }

    static String sanitize(String html) {
        return POLICY.sanitize(html == null ? "" : html);
    }

    static boolean hasMeaningfulContent(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        String text = TAG.matcher(html)
                .replaceAll("")
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .trim();
        return !text.isEmpty() || IMAGE.matcher(html).find();
    }
}
