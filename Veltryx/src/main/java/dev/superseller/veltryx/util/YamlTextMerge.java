package dev.superseller.veltryx.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-based YAML template merge — pure text, no YAML library involved.
 *
 * <p>Merges missing keys and sections from a default template (the config
 * shipped inside a new plugin version) into a user's existing YAML text. Only
 * additions are made: every user value, comment and layout line is kept
 * exactly as it was ("keep my configs"). Comment lines that belong to new
 * keys are copied along, and the operation is idempotent.
 *
 * <p>Limitations (fine for Veltryx's config/message files): single-line
 * {@code key: value} entries, sections ({@code key:}) and {@code #} comments;
 * no multi-line block scalars ({@code |}, {@code >}).
 *
 * <p>This class is pure (no Bukkit) so it can be unit tested offline.
 */
public final class YamlTextMerge {

    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_.\\-]+):(?:\\s.*)?$");

    /** One key/section line with its full path and attached comment start. */
    private record Node(int indent, String path, int line, int commentStart) {
    }

    private YamlTextMerge() {
    }

    /**
     * Adds every key/section of {@code defaultText} that is missing from
     * {@code userText} to the user's text, preserving everything the user
     * already has. Returns the merged text (or the unchanged user text when
     * nothing is missing).
     */
    public static String merge(String defaultText, String userText) {
        String[] userLines = userText.split("\n", -1);
        String[] defaultLines = defaultText.split("\n", -1);

        List<Node> userNodes = parseNodes(userLines);
        Map<String, Integer> userPathLine = new HashMap<>();
        for (Node node : userNodes) {
            userPathLine.putIfAbsent(node.path(), node.line());
        }

        List<Node> nodes = parseNodes(defaultLines);
        int[] subtreeEnd = subtreeEnds(nodes, defaultLines.length);

        // Collect missing blocks (top-most missing nodes, contiguous merged).
        record Block(int start, int end, String parentPath, int parentIndent, boolean rootAppend) {
        }
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        int appendIndex = userLines.length;
        while (appendIndex > 0 && userLines[appendIndex - 1].trim().isEmpty()) {
            appendIndex--;
        }
        while (i < nodes.size()) {
            Node node = nodes.get(i);
            if (userPathLine.containsKey(node.path())) {
                i++;
                continue;
            }
            int start = node.commentStart();
            int end = subtreeEnd[i];
            int dot0 = node.path().lastIndexOf('.');
            // Only merge siblings/subtrees under the same parent section — a run
            // must never swallow unrelated sections further down the file (that
            // nests them wrongly and breaks a second merge).
            String runPrefix = dot0 < 0 ? "" : node.path().substring(0, dot0 + 1);
            i++;
            while (i < nodes.size() && nodes.get(i).commentStart() <= end + 2
                    && nodes.get(i).path().startsWith(runPrefix)
                    && !userPathLine.containsKey(nodes.get(i).path())) {
                end = Math.max(end, subtreeEnd[i]);
                i++;
            }
            int dot = node.path().lastIndexOf('.');
            String parentPath = dot < 0 ? null : node.path().substring(0, dot);
            boolean rootAppend = parentPath == null || !userPathLine.containsKey(parentPath);
            int parentIndent = rootAppend ? -1 : indentOf(userLines[userPathLine.get(parentPath)]);
            blocks.add(new Block(start, end, parentPath, parentIndent, rootAppend));
        }
        if (blocks.isEmpty()) {
            return userText;
        }

        // Render each block and place it: end of the parent section (clamped
        // before trailing blank lines) or the end of the file for new root
        // keys. Blocks at the same spot are ordered deeper-sections-first so
        // the merged text keeps valid nesting (and stays idempotent).
        record Job(int insertAt, int sortKey, List<String> lines) {
        }
        List<Job> jobs = new ArrayList<>();
        for (Block block : blocks) {
            int insertAt;
            int delta = 0;
            if (block.rootAppend()) {
                insertAt = appendIndex;
            } else {
                int parentLine = userPathLine.get(block.parentPath());
                int parentIndent = indentOf(userLines[parentLine]);
                int defaultParentIndent = defaultParentIndent(nodes, block.parentPath());
                delta = parentIndent - defaultParentIndent;
                insertAt = Math.min(sectionEnd(userLines, parentLine, parentIndent), appendIndex);
            }
            List<String> lines = new ArrayList<>();
            if (block.rootAppend() && insertAt > 0 && !userLines[insertAt - 1].trim().isEmpty()) {
                lines.add("");
            }
            for (int n = block.start(); n < block.end(); n++) {
                String line = defaultLines[n];
                if (delta > 0 && !line.trim().isEmpty()) {
                    line = " ".repeat(delta) + line;
                }
                lines.add(line);
            }
            jobs.add(new Job(insertAt, -block.parentIndent(), lines));
        }
        jobs.sort((a, b) -> a.insertAt() != b.insertAt()
                ? Integer.compare(a.insertAt(), b.insertAt())
                : Integer.compare(a.sortKey(), b.sortKey()));
        Map<Integer, List<String>> insertions = new LinkedHashMap<>();
        for (Job job : jobs) {
            insertions.computeIfAbsent(job.insertAt(), k -> new ArrayList<>()).addAll(job.lines());
        }

        StringBuilder out = new StringBuilder();
        for (int n = 0; n <= userLines.length; n++) {
            List<String> added = insertions.get(n);
            if (added != null) {
                for (String line : added) {
                    out.append(line).append('\n');
                }
            }
            if (n < userLines.length) {
                out.append(userLines[n]).append('\n');
            }
        }
        String merged = out.toString();
        if (!userText.endsWith("\n") && merged.endsWith("\n")) {
            merged = merged.substring(0, merged.length() - 1);
        }
        return merged;
    }

    // ------------------------------------------------------------ internals

    private static List<Node> parseNodes(String[] lines) {
        record Frame(int indent, String path) {
        }
        List<Node> nodes = new ArrayList<>();
        List<Frame> stack = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = KEY_LINE.matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            int indent = matcher.group(1).length();
            while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                stack.remove(stack.size() - 1);
            }
            String path = stack.isEmpty()
                    ? matcher.group(2)
                    : stack.get(stack.size() - 1).path() + "." + matcher.group(2);
            int commentStart = i;
            while (commentStart > 0 && lines[commentStart - 1].trim().startsWith("#")) {
                commentStart--;
            }
            nodes.add(new Node(indent, path, i, commentStart));
            stack.add(new Frame(indent, path));
        }
        return nodes;
    }

    private static int[] subtreeEnds(List<Node> nodes, int lineCount) {
        int[] ends = new int[nodes.size()];
        for (int n = 0; n < nodes.size(); n++) {
            int end = lineCount;
            for (int m = n + 1; m < nodes.size(); m++) {
                if (nodes.get(m).indent() <= nodes.get(n).indent()) {
                    end = nodes.get(m).commentStart();
                    break;
                }
            }
            ends[n] = end;
        }
        return ends;
    }

    /** First line after a section's content (next line at the section's level). */
    private static int sectionEnd(String[] lines, int parentLine, int parentIndent) {
        for (int i = parentLine + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                continue;
            }
            if (indentOf(line) <= parentIndent) {
                return i;
            }
        }
        return lines.length;
    }

    private static int defaultParentIndent(List<Node> nodes, String parentPath) {
        for (Node node : nodes) {
            if (node.path().equals(parentPath)) {
                return node.indent();
            }
        }
        return 0;
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }
}
