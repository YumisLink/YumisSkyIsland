package cn.yumis.skyisland.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.*;
import java.util.*;

/** Best-effort syntax recovery. Each statement is parsed by NightConfig, never guessed as a value. */
final class TomlRecovery {
    private static final Set<String> MAPS = Set.of("products", "itemLevels", "itemRarities", "blocks", "items", "ingredientValues", "templates");
    private static final Set<String> OPTIONAL = Set.of("blocks", "basePoints", "acquisitionEnabled", "buyBaseValueCoins", "buyMultiplierBps", "firstAcquisitionPremium", "categoryHint", "shopCategory", "recipeMaterials");
    private TomlRecovery() {}

    static CommentedConfig salvage(String text) {
        CommentedConfig document = CommentedConfig.inMemory(), current = document;
        StringBuilder pending = new StringBuilder();
        Balance balance = new Balance();
        for (String line : text.split("\\R", -1)) {
            // An unterminated array must not swallow subsequent independent assignments/tables.
            if (!balance.complete() && balance.quote == 0 && startsStatement(line)) {
                if (pending.toString().lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).findFirst().orElse("").startsWith("[")) current = null;
                pending.setLength(0); balance = new Balance();
            }
            pending.append(line).append('\n'); balance.scan(line);
            if (!balance.complete()) continue;
            String statement = pending.toString(); pending.setLength(0); balance = new Balance();
            String meaningful = statement.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#")).findFirst().orElse("");
            if (meaningful.isEmpty()) { pending.append(statement); continue; }
            try {
                CommentedConfig parsed = TomlConfigIO.parse(statement);
                if (meaningful.startsWith("[")) current = table(document, parsed);
                else if (current != null) append(current, parsed);
            } catch (RuntimeException ignored) {
                // Values after a malformed header have no trustworthy table until the next header.
                if (meaningful.startsWith("[")) current = null;
            }
        }
        return document;
    }

    private static boolean startsStatement(String line) {
        String stripped = line.stripLeading();
        if (stripped.isEmpty() || stripped.startsWith("#")) return false;
        try {
            // A header containing no assignments parses to an empty table, but it is
            // still a valid place to resume after a damaged multiline value.
            if (stripped.startsWith("[")) {
                TomlConfigIO.parse(line + "\n__toml_recovery_probe__ = true\n");
                return true;
            }
            return !TomlConfigIO.parse(line).isEmpty();
        }
        catch (RuntimeException ignored) { return false; }
    }

    private static CommentedConfig table(CommentedConfig target, UnmodifiableConfig header) {
        if (header.isEmpty()) return target;
        var entry = header.valueMap().entrySet().iterator().next();
        List<String> key = List.of(entry.getKey());
        Object old = target.get(key), value = entry.getValue();
        if (value instanceof List<?> list) {
            List<Object> rows;
            if (old == null) { rows = new ArrayList<>(); target.set(key, rows); }
            else if (old instanceof List<?> existing) {
                @SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) existing; rows = mutable;
            } else throw new IllegalArgumentException("表数组与已有字段冲突");
            CommentedConfig child = CommentedConfig.inMemory(); rows.add(child);
            return table(child, (UnmodifiableConfig) list.getFirst());
        }
        CommentedConfig child;
        if (old == null) { child = CommentedConfig.inMemory(); target.set(key, child); }
        else if (old instanceof CommentedConfig config) child = config;
        else if (old instanceof List<?> list && !list.isEmpty() && list.getLast() instanceof CommentedConfig config) child = config;
        else throw new IllegalArgumentException("表与已有字段冲突");
        if (header instanceof CommentedConfig comments && target.getComment(key) == null) target.setComment(key, comments.getComment(key));
        return table(child, (UnmodifiableConfig) value);
    }

    private static void append(CommentedConfig target, CommentedConfig source) {
        for (var entry : source.valueMap().entrySet()) {
            List<String> key = List.of(entry.getKey());
            Object previous = target.get(key);
            if (previous == null) {
                target.set(key, entry.getValue()); target.setComment(key, source.getComment(key));
            } else if (previous instanceof CommentedConfig old && entry.getValue() instanceof CommentedConfig child) append(old, child);
            // Duplicate scalar keys retain the first readable value.
        }
    }

    static JsonObject complete(JsonObject recovered, JsonObject defaults) {
        return completeValue(recovered, defaults, "").getAsJsonObject();
    }

    private static JsonElement completeValue(JsonElement actual, JsonElement fallback, String key) {
        if (fallback.isJsonNull()) return actual == null ? JsonNull.INSTANCE : actual.deepCopy();
        if (actual == null || !sameType(actual, fallback)) return fallback.deepCopy();
        if (fallback.isJsonObject()) {
            JsonObject source = actual.getAsJsonObject(), schema = fallback.getAsJsonObject(), result = source.deepCopy();
            boolean dictionary = MAPS.contains(key) || schema.keySet().stream().anyMatch(k -> k.contains(":"));
            for (var entry : schema.entrySet()) {
                String field = entry.getKey();
                if (!source.has(field) && (dictionary || OPTIONAL.contains(field))) continue;
                result.add(field, completeValue(source.get(field), entry.getValue(), field));
            }
            return result;
        }
        if (fallback.isJsonArray()) {
            JsonArray source = actual.getAsJsonArray(), schema = fallback.getAsJsonArray(), result = new JsonArray();
            for (int i = 0; i < source.size(); i++) {
                JsonElement row = source.get(i);
                if (schema.isEmpty()) { result.add(row.deepCopy()); continue; }
                JsonElement model = schema.get(Math.min(i, schema.size() - 1));
                if (row.isJsonObject() && row.getAsJsonObject().has("id"))
                    for (JsonElement candidate : schema) if (candidate.isJsonObject() && Objects.equals(candidate.getAsJsonObject().get("id"), row.getAsJsonObject().get("id"))) { model = candidate; break; }
                result.add(completeValue(row, model, key));
            }
            return result;
        }
        return actual.deepCopy();
    }

    private static boolean sameType(JsonElement actual, JsonElement fallback) {
        if (fallback.isJsonObject()) return actual.isJsonObject();
        if (fallback.isJsonArray()) return actual.isJsonArray();
        if (!actual.isJsonPrimitive()) return false;
        JsonPrimitive a = actual.getAsJsonPrimitive(), b = fallback.getAsJsonPrimitive();
        if (b.isBoolean()) return a.isBoolean();
        if (b.isString()) return a.isString();
        if (!a.isNumber()) return false;
        try {
            if (!(b.getAsNumber() instanceof Double) && !(b.getAsNumber() instanceof Float)) a.getAsBigDecimal().longValueExact();
            else if (!Double.isFinite(a.getAsDouble())) return false;
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    /** Track strings/comments so multiline values and quoted registry IDs remain intact. */
    private static final class Balance {
        int square, curly;
        char quote;
        boolean triple, escaped;
        boolean complete() { return quote == 0 && square <= 0 && curly <= 0; }
        void scan(String line) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (quote != 0) {
                    if (escaped) { escaped = false; continue; }
                    if (quote == '"' && c == '\\') { escaped = true; continue; }
                    if (c == quote) {
                        if (!triple) quote = 0;
                        else if (i + 2 < line.length() && line.charAt(i + 1) == quote && line.charAt(i + 2) == quote) { quote = 0; i += 2; }
                    }
                } else if (c == '#') break;
                else if (c == '"' || c == '\'') {
                    quote = c; triple = i + 2 < line.length() && line.charAt(i + 1) == c && line.charAt(i + 2) == c;
                    if (triple) i += 2;
                } else if (c == '[') square++;
                else if (c == ']') square--;
                else if (c == '{') curly++;
                else if (c == '}') curly--;
            }
            if (!triple) quote = 0;
            escaped = false;
        }
    }
}
