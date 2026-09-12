package dev.superseller.minecraftwiki.article;

import java.util.List;

/**
 * One titled block of article content.
 *
 * @param heading     section title, rendered as a header line
 * @param lines       MiniMessage formatted body lines, may be empty
 * @param refs        references rendered underneath the text
 * @param subsections nested sections, may be empty
 */
public record ArticleSection(
        String heading,
        List<String> lines,
        List<ArticleRef> refs,
        List<ArticleSection> subsections
) {

    public static ArticleSection of(String heading, List<String> lines) {
        return new ArticleSection(heading, List.copyOf(lines), List.of(), List.of());
    }

    public static ArticleSection of(String heading, List<String> lines, List<ArticleRef> refs) {
        return new ArticleSection(heading, List.copyOf(lines), List.copyOf(refs), List.of());
    }

    public boolean empty() {
        return (lines == null || lines.isEmpty())
                && (refs == null || refs.isEmpty())
                && (subsections == null || subsections.isEmpty());
    }

    /** Number of rendered entries this section contributes (used for pagination). */
    public int entryCount() {
        int count = 0;
        if (heading != null && !heading.isBlank()) {
            count++;
        }
        count += lines == null ? 0 : lines.size();
        count += refs == null ? 0 : refs.size();
        for (ArticleSection child : subsections == null ? List.<ArticleSection>of() : subsections) {
            count += child.entryCount();
        }
        return count;
    }
}
