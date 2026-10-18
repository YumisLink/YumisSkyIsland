package cn.yumis.skyisland.server;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.TomlConfigIO;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Deep-dark warden bag contents. A bad file keeps the previous valid list and is not rewritten. */
public final class WardenBags {
    public record Grant(String id, int count) {}
    private static final List<Grant> FALLBACK = List.of(new Grant("minecraft:diamond", 64));
    private static volatile List<Grant> contents = FALLBACK;
    private WardenBags() {}

    public static void load() {
        Path file = file();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaults(), StandardCharsets.UTF_8);
            }
            List<Grant> parsed = parse(TomlConfigIO.read(file));
            for (Grant grant : parsed) {
                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(grant.id()))) throw new IllegalArgumentException("未知物品：" + grant.id());
            }
            contents = List.copyOf(parsed);
        } catch (RuntimeException | IOException error) {
            YumisSkyIsland.LOGGER.error("看守者宝藏袋配置无效，保留上一份有效内容：{}", file, error);
        }
    }

    public static List<Grant> parse(JsonObject root) {
        if (root == null || !root.has("items") || !root.get("items").isJsonArray()) throw new IllegalArgumentException("缺少 items 清单");
        JsonArray rows = root.getAsJsonArray("items");
        if (rows.isEmpty()) throw new IllegalArgumentException("宝藏袋清单不能为空");
        List<Grant> parsed = new ArrayList<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("宝藏袋条目必须是物品表");
            JsonObject row = element.getAsJsonObject();
            if (!row.has("id") || !row.get("id").isJsonPrimitive()) throw new IllegalArgumentException("宝藏袋条目缺少物品 ID");
            String id = row.get("id").getAsString();
            int count = row.has("count") ? row.get("count").getAsInt() : 1;
            if (id.isBlank() || count < 1) throw new IllegalArgumentException("宝藏袋条目无效：" + id);
            Identifier.parse(id);
            parsed.add(new Grant(id, count));
        }
        return List.copyOf(parsed);
    }

    public static void give(ServerPlayer player) {
        for (ItemStack stack : stacks()) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    public static List<ItemStack> stacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Grant grant : contents) {
            if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(grant.id()))) continue;
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(grant.id()));
            int left = grant.count();
            int max = Math.max(1, item.getDefaultMaxStackSize());
            while (left > 0) {
                int count = Math.min(max, left);
                stacks.add(new ItemStack(item, count));
                left -= count;
            }
        }
        return stacks;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("yumis_sky_island").resolve("gameplay").resolve("warden_bag.toml");
    }

    private static String defaults() throws IOException {
        try (InputStream in = WardenBags.class.getResourceAsStream("/defaults/yumis_sky_island/gameplay/warden_bag.toml")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return """
                # 深暗之域看守者宝藏袋。打开时按当前文件把每一项都给齐。/reload 重新读取。
                # 写错时保留原文件和上一份有效内容。

                [[items]]
                id = "minecraft:diamond"
                count = 64
                """;
    }
}
