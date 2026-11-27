package com.belopus.life.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RleCodec {

    public static final class DecodedPattern {
        public final CellRegion region;
        public final String name;
        public final String author;
        public final String comment;
        public final RuleSet rule;

        public DecodedPattern(CellRegion region, String name, String author, String comment, RuleSet rule) {
            this.region = region;
            this.name = name;
            this.author = author;
            this.comment = comment;
            this.rule = rule;
        }
    }

    private static final Pattern HEADER_X = Pattern.compile("(?i)\\bx\\s*=\\s*(\\d+)");
    private static final Pattern HEADER_Y = Pattern.compile("(?i)\\by\\s*=\\s*(\\d+)");
    private static final Pattern HEADER_RULE = Pattern.compile("(?i)\\brule\\s*=\\s*([^,\\s]+)");

    private RleCodec() {}

    public static DecodedPattern decodeAuto(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), "", "", "", null);
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        boolean looksLikeRle = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            if (t.toLowerCase().contains("x") && t.contains("=") && t.toLowerCase().contains("y")) {
                looksLikeRle = true;
                break;
            }
            if (t.startsWith("!")) {
                looksLikeRle = false;
                break;
            }
            if (t.contains("$") || t.endsWith("!") || (t.contains("o") && t.contains("b") && !t.contains("."))) {
                looksLikeRle = true;
                break;
            }
            if (t.contains(".") || t.contains("O") || t.contains("*")) {
                looksLikeRle = false;
                break;
            }
        }
        return looksLikeRle ? decodeRle(text) : decodePlaintext(text);
    }

    public static DecodedPattern decodeRle(String rle) {
        if (rle == null) return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), "", "", "", null);

        String name = "";
        String author = "";
        StringBuilder commentSb = new StringBuilder();
        RuleSet rule = null;
        int headerW = 0;
        int headerH = 0;
        boolean headerSeen = false;

        StringBuilder body = new StringBuilder();
        String[] lines = rle.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                if (line.length() >= 2) {
                    char tag = line.charAt(1);
                    String content = line.length() > 2 ? line.substring(2).trim() : "";
                    if (tag == 'N' || tag == 'n') {
                        name = content;
                    } else if (tag == 'O' || tag == 'o') {
                        author = content;
                    } else if (tag == 'C' || tag == 'c') {
                        if (commentSb.length() > 0) commentSb.append('\n');
                        commentSb.append(content);
                    }
                }
                continue;
            }
            if (!headerSeen && (line.toLowerCase().contains("x") && line.contains("="))) {
                headerSeen = true;
                Matcher mx = HEADER_X.matcher(line);
                if (mx.find()) headerW = parseSafeInt(mx.group(1), 0);
                Matcher my = HEADER_Y.matcher(line);
                if (my.find()) headerH = parseSafeInt(my.group(1), 0);
                Matcher mr = HEADER_RULE.matcher(line);
                if (mr.find()) rule = RuleSet.parse(mr.group(1));
                continue;
            }
            body.append(line);
        }

        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        int maxX = Math.max(0, headerW - 1);
        int maxY = Math.max(0, headerH - 1);

        int x = 0;
        int y = 0;
        int count = 0;
        String data = body.toString();
        for (int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c >= '0' && c <= '9') {
                if (count < 1000000) count = count * 10 + (c - '0');
            } else if (c == 'b' || c == '.') {
                int run = count == 0 ? 1 : count;
                x += run;
                count = 0;
            } else if (c == 'o' || c == 'O' || (c >= 'A' && c <= 'Z')) {
                int run = count == 0 ? 1 : count;
                for (int k = 0; k < run; k++) {
                    xs.add(x);
                    ys.add(y);
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                    x++;
                }
                count = 0;
            } else if (c == '$') {
                int run = count == 0 ? 1 : count;
                y += run;
                x = 0;
                if (y > maxY) maxY = y;
                count = 0;
            } else if (c == '!') {
                break;
            }
        }

        if (xs.isEmpty() && headerW <= 0 && headerH <= 0) {
            return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), name, author, commentSb.toString(), rule);
        }
        int w = Math.max(headerW, xs.isEmpty() ? 0 : maxX + 1);
        int h = Math.max(headerH, xs.isEmpty() ? 0 : maxY + 1);
        if (w <= 0 || h <= 0 || (long) w * h > 4_000_000L) {
            return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), name, author, commentSb.toString(), rule);
        }
        boolean[] grid = new boolean[w * h];
        for (int i = 0; i < xs.size(); i++) {
            int cx = xs.get(i);
            int cy = ys.get(i);
            if (cx >= 0 && cy >= 0 && cx < w && cy < h) {
                grid[cy * w + cx] = true;
            }
        }
        return new DecodedPattern(new CellRegion(w, h, grid), name, author, commentSb.toString(), rule);
    }

    public static DecodedPattern decodePlaintext(String text) {
        if (text == null) return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), "", "", "", null);
        String name = "";
        StringBuilder commentSb = new StringBuilder();
        List<String> rows = new ArrayList<>();
        int maxW = 0;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            if (line.startsWith("!")) {
                String c = line.substring(1).trim();
                if (c.toLowerCase().startsWith("name:")) {
                    name = c.substring(5).trim();
                } else if (!c.isEmpty()) {
                    if (commentSb.length() > 0) commentSb.append('\n');
                    commentSb.append(c);
                }
                continue;
            }
            if (line.trim().isEmpty() && rows.isEmpty()) continue;
            rows.add(line);
            if (line.length() > maxW) maxW = line.length();
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).trim().isEmpty()) {
            rows.remove(rows.size() - 1);
        }
        int h = rows.size();
        if (maxW <= 0 || h <= 0) {
            return new DecodedPattern(new CellRegion(0, 0, new boolean[0]), name, "", commentSb.toString(), null);
        }
        boolean[] grid = new boolean[maxW * h];
        for (int y = 0; y < h; y++) {
            String row = rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == 'O' || c == 'o' || c == '*' || c == 'X' || c == 'x' || c == '1') {
                    grid[y * maxW + x] = true;
                }
            }
        }
        return new DecodedPattern(new CellRegion(maxW, h, grid), name, "", commentSb.toString(), null);
    }

    public static String encodeRle(CellRegion region, RuleSet rule, String name, String comment) {
        CellRegion trimmed = region != null ? region.trimmed() : new CellRegion(0, 0, new boolean[0]);
        StringBuilder out = new StringBuilder();
        if (name != null && !name.trim().isEmpty()) {
            out.append("#N ").append(name.trim()).append('\n');
        }
        if (comment != null && !comment.trim().isEmpty()) {
            for (String line : comment.split("\n")) {
                out.append("#C ").append(line.trim()).append('\n');
            }
        }
        String ruleCode = rule != null ? rule.getRuleCode() : "B3/S23";
        if (trimmed.width == 0 || trimmed.height == 0) {
            out.append("x = 0, y = 0, rule = ").append(ruleCode).append("\n!\n");
            return out.toString();
        }
        out.append("x = ").append(trimmed.width)
           .append(", y = ").append(trimmed.height)
           .append(", rule = ").append(ruleCode).append('\n');

        StringBuilder rleStream = new StringBuilder();
        int pendingEmptyLines = 0;

        for (int y = 0; y < trimmed.height; y++) {
            int lastLiveX = -1;
            for (int x = trimmed.width - 1; x >= 0; x--) {
                if (trimmed.get(x, y)) {
                    lastLiveX = x;
                    break;
                }
            }
            if (lastLiveX < 0) {
                pendingEmptyLines++;
                continue;
            }
            if (pendingEmptyLines > 0) {
                appendRun(rleStream, pendingEmptyLines, '$');
                pendingEmptyLines = 0;
            }
            boolean curState = trimmed.get(0, y);
            int runLen = 1;
            for (int x = 1; x <= lastLiveX; x++) {
                boolean st = trimmed.get(x, y);
                if (st == curState) {
                    runLen++;
                } else {
                    appendRun(rleStream, runLen, curState ? 'o' : 'b');
                    curState = st;
                    runLen = 1;
                }
            }
            appendRun(rleStream, runLen, curState ? 'o' : 'b');
            if (y < trimmed.height - 1) {
                pendingEmptyLines = 1;
            }
        }
        rleStream.append('!');

        // Wrap at 70 characters without splitting tokens if possible
        int lineLen = 0;
        int i = 0;
        while (i < rleStream.length()) {
            int j = i;
            while (j < rleStream.length() && Character.isDigit(rleStream.charAt(j))) j++;
            if (j < rleStream.length()) j++; // include command char
            int tokenLen = j - i;
            if (lineLen > 0 && lineLen + tokenLen > 70) {
                out.append('\n');
                lineLen = 0;
            }
            out.append(rleStream, i, j);
            lineLen += tokenLen;
            i = j;
        }
        out.append('\n');
        return out.toString();
    }

    private static void appendRun(StringBuilder sb, int count, char ch) {
        if (count > 1) sb.append(count);
        sb.append(ch);
    }

    private static int parseSafeInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
