package autosummon.compat;

import autosummon.api.AutoSummonAPI;
import autosummon.api.SummonWeaponSpec;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import autosummon.util.AutoSummonLog;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Discovers, parses, and validates data-driven {@code autosummon.json} configuration files
 * from loaded mod JAR resources and filesystems.
 *
 * <p>Features comprehensive error handling, permissive JSON5 parsing (comments, unquoted keys,
 * single quotes, trailing commas), exact line/column syntax error reporting, and resilient
 * per-entry recovery so a single typo does not break other valid registrations.</p>
 */
public final class AutoSummonJsonLoader {

    private static final String[] CONFIG_RESOURCE_NAMES = {
            "autosummon.json",
            "autosummon_compat.json",
            "assets/autosummon.json",
            "res/autosummon.json"
    };

    private static volatile boolean loaded = false;

    private AutoSummonJsonLoader() {}

    /**
     * Result report of a JSON configuration load and validation pass.
     */
    public static class JsonLoadResult {
        public final String sourceIdentifier;
        public String modId = "unknown";
        public int weaponsRegistered = 0;
        public int ignoredMobsRegistered = 0;
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public JsonLoadResult(String sourceIdentifier) {
            this.sourceIdentifier = sourceIdentifier != null ? sourceIdentifier : "unknown";
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }

        public boolean hasIssues() {
            return !warnings.isEmpty() || !errors.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("JsonLoadResult for '").append(sourceIdentifier).append("' (Mod: ").append(modId).append("): ")
                    .append(weaponsRegistered).append(" weapons, ")
                    .append(ignoredMobsRegistered).append(" ignored mobs. ");
            if (!errors.isEmpty()) {
                sb.append("Errors (").append(errors.size()).append("): ").append(errors).append("; ");
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings (").append(warnings.size()).append("): ").append(warnings);
            }
            return sb.toString();
        }
    }

    /**
     * Scan classpaths and loaded resources for any autosummon.json definitions and load them.
     */
    public static synchronized void loadAllConfigs() {
        if (loaded) return;

        int totalLoaded = 0;
        ClassLoader[] loaders = {
                AutoSummonJsonLoader.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        Set<String> processedUrls = new HashSet<>();

        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            for (String resName : CONFIG_RESOURCE_NAMES) {
                try {
                    Enumeration<URL> urls = cl.getResources(resName);
                    while (urls.hasMoreElements()) {
                        URL url = urls.nextElement();
                        String urlString = url.toString();
                        if (processedUrls.add(urlString)) {
                            try (InputStream is = url.openStream()) {
                                if (is != null) {
                                    JsonLoadResult result = parseAndRegister(is, urlString);
                                    totalLoaded += result.weaponsRegistered;
                                }
                            } catch (Throwable t) {
                                AutoSummonLog.error("I/O error reading AutoSummon JSON config from: " + urlString, t);
                            }
                        }
                    }
                } catch (Throwable t) {
                    AutoSummonLog.debug("Could not query resource '" + resName + "' on classloader: " + t.getMessage());
                }
            }
        }

        loaded = true;
        if (totalLoaded > 0) {
            AutoSummonLog.info("AutoSummon JSON loader successfully registered " + totalLoaded + " data-driven configurations.");
        }
    }

    /**
     * Parse JSON from an input stream and register declared weapons and ignored mobs.
     */
    public static JsonLoadResult parseAndRegister(InputStream inputStream, String sourceIdentifier) throws IOException {
        String jsonText = readStreamToString(inputStream);
        return parseAndRegisterJsonString(jsonText, sourceIdentifier);
    }

    /**
     * Parse JSON string directly with full validation and error reporting.
     */
    public static JsonLoadResult parseAndRegisterJsonString(String jsonText, String sourceIdentifier) {
        JsonLoadResult result = new JsonLoadResult(sourceIdentifier);

        if (jsonText == null || jsonText.trim().isEmpty()) {
            result.warnings.add("JSON content is empty or blank.");
            return result;
        }

        // Clean UTF-8 BOM if present
        if (jsonText.startsWith("\uFEFF")) {
            jsonText = jsonText.substring(1);
        }

        SimpleJsonParser parser = new SimpleJsonParser(jsonText, sourceIdentifier);
        Object parsedRoot = parser.parse();

        if (parser.hasSyntaxError()) {
            result.errors.addAll(parser.getSyntaxErrors());
            for (String err : parser.getSyntaxErrors()) {
                AutoSummonLog.error("JSON Syntax Error in '" + sourceIdentifier + "': " + err);
            }
            return result;
        }

        SimpleJsonObject root = null;
        List<SimpleJsonObject> weaponListDirect = null;

        if (parsedRoot instanceof SimpleJsonObject) {
            root = (SimpleJsonObject) parsedRoot;
        } else if (parsedRoot instanceof List) {
            // Permissive support: Modder declared top-level array [ {...}, {...} ] directly
            weaponListDirect = parser.castToObjectList((List<?>) parsedRoot);
            root = new SimpleJsonObject();
            root.put("weapons", weaponListDirect);
            result.warnings.add("Top-level JSON is an array rather than an object. Treated as 'weapons' list.");
        } else {
            result.errors.add("Root JSON element must be an object '{ ... }' or array '[ ... ]', found: " +
                    (parsedRoot == null ? "null" : parsedRoot.getClass().getSimpleName()));
            AutoSummonLog.error("Invalid JSON structure in '" + sourceIdentifier + "': " + result.errors.get(0));
            return result;
        }

        result.modId = root.getString("modId", "unknown");

        // 1. Process "weapons" array
        List<SimpleJsonObject> weapons = root.getObjectList("weapons");
        if (weapons != null) {
            int entryIndex = 0;
            for (SimpleJsonObject wObj : weapons) {
                entryIndex++;
                if (wObj == null) {
                    result.warnings.add("Weapon entry #" + entryIndex + " is null, skipping.");
                    continue;
                }

                String rawId = wObj.getString("id", null);
                if (rawId == null || rawId.trim().isEmpty()) {
                    String warn = "Weapon entry #" + entryIndex + " is missing required 'id' field, skipping.";
                    result.warnings.add(warn);
                    AutoSummonLog.warn("[" + sourceIdentifier + "] " + warn);
                    continue;
                }

                String id = rawId.trim().toLowerCase();
                String displayName = wObj.getString("displayName", null);
                String categoryStr = wObj.getString("category", null);
                String typeStr = wObj.getString("type", null);

                // Parse category with error handling
                SummonCategory category = parseCategory(categoryStr, typeStr, result, id);

                // Parse slot cost safely
                float slotCost = 1.0f;
                Object rawSlotCost = wObj.get("slotCost");
                if (rawSlotCost != null) {
                    slotCost = parseSafeFloat(rawSlotCost, 1.0f, result, id, "slotCost");
                    if (slotCost <= 0) {
                        result.warnings.add("Weapon '" + id + "' has non-positive slotCost (" + slotCost + "), defaulting to 1.0");
                        slotCost = 1.0f;
                    }
                }

                // Parse max allowed safely
                int maxAllowed = -1;
                Object rawMaxAllowed = wObj.get("maxAllowed");
                if (rawMaxAllowed != null) {
                    maxAllowed = parseSafeInt(rawMaxAllowed, -1, result, id, "maxAllowed");
                    if (maxAllowed == 0) {
                        maxAllowed = 1;
                    }
                }

                // Collect minions
                Set<String> minions = new HashSet<>();
                String singleMinion = wObj.getString("minion", null);
                if (singleMinion != null && !singleMinion.trim().isEmpty()) {
                    minions.add(singleMinion.trim().toLowerCase());
                }

                List<String> minionList = wObj.getStringList("minions");
                if (minionList != null) {
                    for (String m : minionList) {
                        if (m != null && !m.trim().isEmpty()) {
                            minions.add(m.trim().toLowerCase());
                        }
                    }
                }

                // Warning if no minions defined
                if (minions.isEmpty()) {
                    result.warnings.add("Weapon '" + id + "' has no 'minion' or 'minions' declared. Auto Summon will rely on item ID matching.");
                }

                SummonWeaponSpec.Builder builder = SummonWeaponSpec.builder(id)
                        .displayName(displayName)
                        .category(category)
                        .slotCost(slotCost)
                        .maxAllowed(maxAllowed)
                        .withMinions(minions);

                if (typeStr != null) {
                    String cleanType = typeStr.trim().toLowerCase();
                    if (cleanType.equals("follower") || cleanType.equals("secondary")) {
                        builder.asFollower(maxAllowed > 0 ? maxAllowed : 1);
                    } else if (cleanType.equals("sentry") || cleanType.equals("support") || cleanType.equals("turret")) {
                        builder.asSentry(maxAllowed > 0 ? maxAllowed : 1);
                    } else if (cleanType.equals("global") || cleanType.equals("slot")) {
                        builder.asGlobalSlot(slotCost);
                    }
                }

                try {
                    AutoSummonAPI.registerWeapon(builder.build());
                    result.weaponsRegistered++;
                } catch (Throwable t) {
                    String err = "Failed to register weapon '" + id + "': " + t.getMessage();
                    result.errors.add(err);
                    AutoSummonLog.error("[" + sourceIdentifier + "] " + err, t);
                }
            }
        } else if (!root.hasKey("weapons") && !root.hasKey("ignoredMobs")) {
            result.warnings.add("JSON contains neither 'weapons' nor 'ignoredMobs' keys.");
        }

        // 2. Process "ignoredMobs"
        List<String> ignoredMobs = root.getStringList("ignoredMobs");
        if (ignoredMobs == null) {
            String singleIgnored = root.getString("ignoredMobs", null);
            if (singleIgnored != null && !singleIgnored.trim().isEmpty()) {
                ignoredMobs = Collections.singletonList(singleIgnored.trim());
            }
        }

        if (ignoredMobs != null) {
            for (String mobID : ignoredMobs) {
                if (mobID != null && !mobID.trim().isEmpty()) {
                    try {
                        AutoSummonAPI.ignoreMob(mobID.trim());
                        result.ignoredMobsRegistered++;
                    } catch (Throwable t) {
                        result.warnings.add("Failed to register ignored mob '" + mobID + "': " + t.getMessage());
                    }
                }
            }
        }

        if (result.hasIssues()) {
            AutoSummonLog.debug(result.toString());
        }

        return result;
    }

    private static SummonCategory parseCategory(String categoryStr, String typeStr, JsonLoadResult result, String itemId) {
        String target = categoryStr != null ? categoryStr : typeStr;
        if (target == null || target.trim().isEmpty()) {
            return SummonCategory.GLOBAL_SLOT;
        }

        String clean = target.trim().toUpperCase();
        if (clean.contains("FOLLOWER") || clean.contains("SECONDARY")) {
            return SummonCategory.INDEPENDENT_FOLLOWER;
        }
        if (clean.contains("SENTRY") || clean.contains("SUPPORT") || clean.contains("TURRET") || clean.contains("TOTEM")) {
            return SummonCategory.SENTRY_SUPPORT;
        }
        if (clean.contains("GLOBAL") || clean.contains("SLOT")) {
            return SummonCategory.GLOBAL_SLOT;
        }

        result.warnings.add("Unrecognized category '" + target + "' for weapon '" + itemId + "', defaulting to GLOBAL_SLOT.");
        return SummonCategory.GLOBAL_SLOT;
    }

    private static float parseSafeFloat(Object val, float defaultVal, JsonLoadResult result, String itemId, String fieldName) {
        if (val instanceof Number) {
            float f = ((Number) val).floatValue();
            return Float.isNaN(f) || Float.isInfinite(f) ? defaultVal : f;
        }
        if (val instanceof String) {
            try {
                float f = Float.parseFloat(((String) val).trim());
                return Float.isNaN(f) || Float.isInfinite(f) ? defaultVal : f;
            } catch (Throwable t) {
                result.warnings.add("Field '" + fieldName + "' for weapon '" + itemId + "' has invalid numeric value '" + val + "', using " + defaultVal);
            }
        }
        return defaultVal;
    }

    private static int parseSafeInt(Object val, int defaultVal, JsonLoadResult result, String itemId, String fieldName) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val instanceof String) {
            try {
                return Integer.parseInt(((String) val).trim());
            } catch (Throwable t) {
                result.warnings.add("Field '" + fieldName + "' for weapon '" + itemId + "' has invalid integer value '" + val + "', using " + defaultVal);
            }
        }
        return defaultVal;
    }

    private static String readStreamToString(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    public static synchronized void resetForTesting() {
        loaded = false;
    }

    // =========================================================================
    // Robust, Permissive JSON5 Tokenizer and Parser with Line/Column Tracking
    // =========================================================================

    public static class SimpleJsonObject {
        private final Map<String, Object> data = new LinkedHashMap<>();

        public void put(String key, Object val) {
            if (key != null) {
                data.put(key, val);
            }
        }

        public Object get(String key) {
            return data.get(key);
        }

        public boolean hasKey(String key) {
            return data.containsKey(key);
        }

        public String getString(String key, String def) {
            Object val = data.get(key);
            return val != null ? val.toString() : def;
        }

        public int getInt(String key, int def) {
            Object val = data.get(key);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val instanceof String) {
                try { return Integer.parseInt(((String) val).trim()); } catch (Throwable ignored) {}
            }
            return def;
        }

        public double getDouble(String key, double def) {
            Object val = data.get(key);
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val instanceof String) {
                try { return Double.parseDouble(((String) val).trim()); } catch (Throwable ignored) {}
            }
            return def;
        }

        public List<String> getStringList(String key) {
            Object val = data.get(key);
            if (val instanceof List) {
                List<?> list = (List<?>) val;
                List<String> res = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) res.add(item.toString());
                }
                return res;
            }
            return null;
        }

        public List<SimpleJsonObject> getObjectList(String key) {
            Object val = data.get(key);
            if (val instanceof List) {
                List<?> list = (List<?>) val;
                List<SimpleJsonObject> res = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof SimpleJsonObject) {
                        res.add((SimpleJsonObject) item);
                    }
                }
                return res;
            }
            return null;
        }
    }

    public static class SimpleJsonParser {
        private final String src;
        private final String sourceIdentifier;
        private int pos = 0;
        private int line = 1;
        private int col = 1;
        private final List<String> syntaxErrors = new ArrayList<>();

        public SimpleJsonParser(String src, String sourceIdentifier) {
            this.src = src != null ? src : "";
            this.sourceIdentifier = sourceIdentifier != null ? sourceIdentifier : "unknown";
        }

        public static SimpleJsonObject parseObject(String json) {
            if (json == null || json.trim().isEmpty()) return null;
            SimpleJsonParser parser = new SimpleJsonParser(json, "direct");
            Object result = parser.parse();
            return result instanceof SimpleJsonObject ? (SimpleJsonObject) result : null;
        }

        public boolean hasSyntaxError() {
            return !syntaxErrors.isEmpty();
        }

        public List<String> getSyntaxErrors() {
            return syntaxErrors;
        }

        public Object parse() {
            skipWhitespaceAndComments();
            if (pos >= src.length()) {
                return null;
            }
            Object result = parseValue();
            skipWhitespaceAndComments();
            if (pos < src.length() && !hasSyntaxError()) {
                syntaxErrors.add(formatError("Unexpected trailing character '" + src.charAt(pos) + "'"));
            }
            return result;
        }

        public String getSourceIdentifier() {
            return sourceIdentifier;
        }

        private String formatError(String message) {
            return message + " (in '" + sourceIdentifier + "') at line " + line + ", column " + col;
        }

        private void advance() {
            if (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
        }

        private char peek() {
            return pos < src.length() ? src.charAt(pos) : '\0';
        }

        private void skipWhitespaceAndComments() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isWhitespace(c) || c == '\r' || c == '\n' || c == '\t') {
                    advance();
                } else if (c == '/' && pos + 1 < src.length()) {
                    char next = src.charAt(pos + 1);
                    if (next == '/') {
                        // Single-line comment: // ...
                        advance(); advance();
                        while (pos < src.length() && src.charAt(pos) != '\n') {
                            advance();
                        }
                    } else if (next == '*') {
                        // Multi-line comment: /* ... */
                        advance(); advance();
                        boolean closed = false;
                        while (pos + 1 < src.length()) {
                            if (src.charAt(pos) == '*' && src.charAt(pos + 1) == '/') {
                                advance(); advance();
                                closed = true;
                                break;
                            }
                            advance();
                        }
                        if (!closed) {
                            syntaxErrors.add(formatError("Unclosed multi-line comment '/*'"));
                            break;
                        }
                    } else {
                        break;
                    }
                } else if (c == '#') {
                    // Hash / shell comment: # ...
                    advance();
                    while (pos < src.length() && src.charAt(pos) != '\n') {
                        advance();
                    }
                } else {
                    break;
                }
            }
        }

        private Object parseValue() {
            skipWhitespaceAndComments();
            if (pos >= src.length()) {
                syntaxErrors.add(formatError("Unexpected end of JSON input"));
                return null;
            }

            char c = peek();
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"' || c == '\'') return parseString();
            if (c == 't' || c == 'T' || c == 'f' || c == 'F') return parseBoolean();
            if (c == 'n' || c == 'N') return parseNull();
            if (Character.isDigit(c) || c == '-' || c == '+') return parseNumber();

            // Permissive unquoted identifier as value
            if (Character.isJavaIdentifierStart(c)) {
                return parseUnquotedIdentifier();
            }

            syntaxErrors.add(formatError("Unexpected token '" + c + "'"));
            advance();
            return null;
        }

        private SimpleJsonObject parseObject() {
            SimpleJsonObject obj = new SimpleJsonObject();
            advance(); // consume '{'

            while (pos < src.length()) {
                skipWhitespaceAndComments();
                if (pos >= src.length()) {
                    syntaxErrors.add(formatError("Unclosed object: missing '}'"));
                    break;
                }

                if (peek() == '}') {
                    advance();
                    return obj;
                }

                // Parse key (quoted or unquoted identifier)
                String key;
                char c = peek();
                if (c == '"' || c == '\'') {
                    key = parseString();
                } else if (Character.isJavaIdentifierStart(c) || c == '_' || c == '$') {
                    key = parseUnquotedIdentifier();
                } else {
                    syntaxErrors.add(formatError("Expected object key, found '" + c + "'"));
                    advance();
                    continue;
                }

                skipWhitespaceAndComments();
                if (peek() == ':') {
                    advance();
                } else {
                    syntaxErrors.add(formatError("Expected ':' after key '" + key + "', found '" + peek() + "'"));
                }

                Object val = parseValue();
                if (key != null) {
                    obj.put(key, val);
                }

                skipWhitespaceAndComments();
                if (peek() == ',') {
                    advance();
                    // Tolerant: allow trailing comma before '}'
                    skipWhitespaceAndComments();
                    if (peek() == '}') {
                        advance();
                        return obj;
                    }
                } else if (peek() != '}') {
                    syntaxErrors.add(formatError("Expected ',' or '}' in object, found '" + peek() + "'"));
                    if (peek() != '\0') advance();
                }
            }

            return obj;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            advance(); // consume '['

            while (pos < src.length()) {
                skipWhitespaceAndComments();
                if (pos >= src.length()) {
                    syntaxErrors.add(formatError("Unclosed array: missing ']'"));
                    break;
                }

                if (peek() == ']') {
                    advance();
                    return list;
                }

                Object val = parseValue();
                list.add(val);

                skipWhitespaceAndComments();
                if (peek() == ',') {
                    advance();
                    // Tolerant: allow trailing comma before ']'
                    skipWhitespaceAndComments();
                    if (peek() == ']') {
                        advance();
                        return list;
                    }
                } else if (peek() != ']') {
                    syntaxErrors.add(formatError("Expected ',' or ']' in array, found '" + peek() + "'"));
                    if (peek() != '\0') advance();
                }
            }

            return list;
        }

        private String parseString() {
            char quote = peek();
            if (quote != '"' && quote != '\'') return "";
            advance();

            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = peek();
                if (c == quote) {
                    advance();
                    return sb.toString();
                }

                if (c == '\\') {
                    advance();
                    if (pos >= src.length()) break;
                    char esc = peek();
                    advance();
                    switch (esc) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '"': sb.append('"'); break;
                        case '\'': sb.append('\''); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            // Unicode 4 hex digits: e.g. \\u0041
                            if (pos + 4 <= src.length()) {
                                String hex = src.substring(pos, pos + 4);
                                try {
                                    int codePoint = Integer.parseInt(hex, 16);
                                    sb.append((char) codePoint);
                                    for (int i = 0; i < 4; i++) advance();
                                } catch (Throwable t) {
                                    sb.append("\\u").append(hex);
                                }
                            }
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else {
                    sb.append(c);
                    advance();
                }
            }

            syntaxErrors.add(formatError("Unterminated string literal: missing closing " + quote));
            return sb.toString();
        }

        private String parseUnquotedIdentifier() {
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = peek();
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$' || c == '.') {
                    sb.append(c);
                    advance();
                } else {
                    break;
                }
            }
            return sb.toString();
        }

        private Boolean parseBoolean() {
            String ident = parseUnquotedIdentifier().toLowerCase();
            if (ident.equals("true")) return true;
            if (ident.equals("false")) return false;
            syntaxErrors.add(formatError("Invalid boolean literal '" + ident + "'"));
            return false;
        }

        private Object parseNull() {
            String ident = parseUnquotedIdentifier().toLowerCase();
            if (!ident.equals("null")) {
                syntaxErrors.add(formatError("Invalid null literal '" + ident + "'"));
            }
            return null;
        }

        private Number parseNumber() {
            StringBuilder sb = new StringBuilder();

            if (peek() == '-' || peek() == '+') {
                sb.append(peek());
                advance();
            }

            boolean isFloat = false;
            while (pos < src.length()) {
                char c = peek();
                if (Character.isDigit(c)) {
                    sb.append(c);
                    advance();
                } else if (c == '.' || c == 'e' || c == 'E') {
                    isFloat = true;
                    sb.append(c);
                    advance();
                    if (pos < src.length() && (peek() == '-' || peek() == '+')) {
                        sb.append(peek());
                        advance();
                    }
                } else {
                    break;
                }
            }

            String numStr = sb.toString();
            try {
                if (isFloat) {
                    return Double.parseDouble(numStr);
                } else {
                    long val = Long.parseLong(numStr);
                    if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                        return (int) val;
                    }
                    return val;
                }
            } catch (Throwable t) {
                syntaxErrors.add(formatError("Invalid number literal '" + numStr + "'"));
                return 0;
            }
        }

        public List<SimpleJsonObject> castToObjectList(List<?> rawList) {
            List<SimpleJsonObject> list = new ArrayList<>();
            if (rawList != null) {
                for (Object item : rawList) {
                    if (item instanceof SimpleJsonObject) {
                        list.add((SimpleJsonObject) item);
                    }
                }
            }
            return list;
        }
    }
}
