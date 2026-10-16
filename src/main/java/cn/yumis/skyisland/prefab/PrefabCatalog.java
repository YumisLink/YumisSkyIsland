package cn.yumis.skyisland.prefab;

import cn.yumis.skyisland.YumisSkyIsland;
import cn.yumis.skyisland.config.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;

public final class PrefabCatalog {
    public record Definition(String id,String name,String category,String author,String source,String license,String file,
        String status,int level,String rarity,long blueprintPrice,long buildFee,Set<Integer> rotations,String dimension,
        String biome,int alignX,int alignZ,String notes,String sha256) {}
    public final Map<String,Definition> definitions=new LinkedHashMap<>();
    private final Map<String,PrefabTemplate> cache=new LinkedHashMap<>();
    private final Set<String> registeredBlueprints=new LinkedHashSet<>();
    public final Path directory;
    private final PrefabRules rules;
    public PrefabCatalog(GameConfig config,PrefabRules rules) throws IOException {
        this.rules=rules;Path game=FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();directory=game.resolve(rules.directory()).normalize();
        if(!directory.startsWith(game))throw new IOException("建筑库目录越出运行实例");Files.createDirectories(directory);
        Path manifest=directory.resolve("catalog.toml");if(!Files.exists(manifest))return;
        JsonObject data=TomlConfigIO.readOrRepair(manifest,"# 外置建筑目录；仅保留实际安装的模板。\n[templates]\n");if(!data.has("templates"))return;
        for(var entry:data.getAsJsonObject("templates").entrySet()) {
            String id=entry.getKey();if(!id.matches("[a-z0-9_]{1,80}"))throw new IOException("建筑ID无效："+id);
            JsonObject o=entry.getValue().getAsJsonObject();Set<Integer> rotations=new LinkedHashSet<>();
            for(var r:o.getAsJsonArray("rotations")){int value=r.getAsInt();if(value<0||value>3)throw new IOException("建筑方向无效："+id);rotations.add(value);}
            Definition d=new Definition(id,s(o,"name",id),s(o,"category","生电"),s(o,"author",""),s(o,"sourceUrl",""),s(o,"usage",""),
                s(o,"file",id+".yprefab"),s(o,"status","pending"),i(o,"level",10),s(o,"rarity","common"),
                l(o,"blueprintPrice",1000000),l(o,"buildFee",20000000),Set.copyOf(rotations),s(o,"dimension",""),s(o,"biome",""),i(o,"alignX",1),i(o,"alignZ",1),s(o,"notes",""),s(o,"sha256",""));
            if(rotations.isEmpty()||d.level<0||d.level>100||d.blueprintPrice<1||d.buildFee<1||d.alignX<1||d.alignZ<1||!Set.of("common","rare","epic","legendary").contains(d.rarity))throw new IOException("建筑价格、等级或方向无效："+id);
            definitions.put(id,d);
            if(available(d))try{load(id);addProduct(config,d);registeredBlueprints.add(id);}catch(Exception e){YumisSkyIsland.LOGGER.error("预制建筑 {} 未上架：{}",id,e.getMessage());}
        }
    }
    public Set<String> registeredBlueprints(){return Collections.unmodifiableSet(registeredBlueprints);}
    private static String s(JsonObject o,String key,String fallback){return o.has(key)?o.get(key).getAsString():fallback;}
    private static int i(JsonObject o,String key,int fallback){return o.has(key)?o.get(key).getAsInt():fallback;}
    private static long l(JsonObject o,String key,long fallback){return o.has(key)?o.get(key).getAsLong():fallback;}
    public boolean available(Definition d){return d!=null&&(d.status.equals("verified")||!rules.requireVerified()&&d.status.equals("imported"))&&Files.isRegularFile(file(d));}
    public Path file(Definition d){Path path=directory.resolve(d.file).normalize();if(!path.startsWith(directory))throw new IllegalArgumentException("模板文件越出建筑库");return path;}
    public PrefabTemplate load(String id) throws IOException {
        Definition d=definitions.get(id);if(d==null)throw new IllegalArgumentException("建筑不存在");
        if(cache.containsKey(id))return cache.get(id);
        if(Files.size(file(d))>32*1024*1024)throw new IOException("模板压缩文件过大");byte[] bytes=Files.readAllBytes(file(d));
        try {
            String revision=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(d.status.equals("verified")&&!revision.equals(d.sha256))throw new IOException("已验收模板的SHA256不符，请重新验收此文件");
            byte[] json;try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))){json=gzip.readNBytes(128*1024*1024+1);}
            if(json.length>128*1024*1024)throw new IOException("模板解压后过大");
            PrefabTemplate template=new PrefabTemplate(JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject(),revision,rules);
            cache.put(id,template);return template;
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("模板无法读取："+id+"："+e.getMessage(),e);}
    }
    public PrefabTemplate pinned(Path file,String revision) throws IOException {
        if(Files.size(file)>32*1024*1024)throw new IOException("施工模板压缩文件过大");byte[] bytes=Files.readAllBytes(file);
        try {
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(revision))throw new IOException("施工模板校验失败");
            try(var gzip=new GZIPInputStream(new ByteArrayInputStream(bytes))){byte[] data=gzip.readNBytes(128*1024*1024+1);if(data.length>128*1024*1024)throw new IOException("施工模板过大");return new PrefabTemplate(JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject(),revision,rules);}
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
    }
    public void pin(Definition definition,PrefabTemplate template,Path target)throws IOException {
        Path source=Files.exists(target)?target:file(definition);if(Files.size(source)>32*1024*1024)throw new IOException("施工模板压缩文件过大");byte[] bytes=Files.readAllBytes(source);
        try{if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(template.revision))throw new IOException("开工模板在定位后改变，请重新加载并验收建筑库");}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
        if(Files.exists(target))return;Path temporary=target.resolveSibling(target.getFileName()+"."+UUID.randomUUID()+".tmp");
        try{try(var out=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){var data=java.nio.ByteBuffer.wrap(bytes);while(data.hasRemaining())out.write(data);out.force(true);}Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
        finally{Files.deleteIfExists(temporary);}
    }
    private static void addProduct(GameConfig config,Definition d) {
        String id="yumis_sky_island:prefab/"+d.id;JsonObject canonical=new JsonObject(),patch=new JsonObject(),data=new JsonObject();
        canonical.addProperty("itemId","yumis_sky_island:prefab_blueprint");data.addProperty("yumis_prefab",d.id);
        patch.add("minecraft:custom_data",data);patch.addProperty("minecraft:item_name",d.name+"图纸");canonical.add("componentPatch",patch);
        JsonObject row=new JsonObject();row.addProperty("productId",id);row.addProperty("name",d.name+"图纸");row.addProperty("tradeMode","buy_only");row.addProperty("baseValueCoins",d.blueprintPrice);
        row.addProperty("buyBaseValueCoins",d.blueprintPrice);row.addProperty("buyMultiplierBps",10000);row.addProperty("dailyStock",1);row.addProperty("offerWeight",1);row.addProperty("shopCategory","minecraft:tools_and_utilities");row.add("canonicalStack",canonical);
        // Explicit administrator overrides in the product TOML win over generated defaults.
        if(config.products.containsKey(id))return;
        JsonObject overrides=config.object("shop/products.toml").getAsJsonObject("buyBaseValueOverrides");
        long buy=overrides!=null&&overrides.has(id)?ScoringSettings.integer(overrides.get(id),1,Long.MAX_VALUE):d.blueprintPrice;
        config.products.put(id,new GameConfig.Product(id,d.name+"图纸",canonical,d.blueprintPrice,buy,10000,"buy_only",d.rarity,d.level,1,1,"minecraft:tools_and_utilities"));
    }
    public JsonArray describe() {
        JsonArray array=new JsonArray();for(var d:definitions.values()){
            JsonObject row=new JsonObject();row.addProperty("id",d.id);row.addProperty("name",d.name);row.addProperty("category",d.category);row.addProperty("author",d.author);row.addProperty("source",d.source);row.addProperty("notes",d.notes);row.addProperty("status",d.status);row.addProperty("available",available(d));array.add(row);
        }return array;
    }
}
