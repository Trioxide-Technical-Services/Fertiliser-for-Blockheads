package com.goldmike.fertiliserforblockheads;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.slf4j.Logger;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
@EventBusSubscriber(modid = FertiliserForBlockheads.MODID)
public final class BotanyPotsCompat
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String CATEGORY_FARMLAND = "farmland";
    private static final String CATEGORY_FARMLAND_RICH = "farmland_rich";
    static final String GENERATED_PACK_ID = FertiliserForBlockheads.MODID + "_botany_pots_compat";
    static final String LEGACY_GENERATED_PACK_ID = FertiliserForBlockheads.MODID + "_botany_pots_comapt";
    static final String LEGACY_WORLD_PACK_ID = FertiliserForBlockheads.MODID + "_generated_botanypots_soils";
    private static final Path GENERATED_PACK_ROOT = FMLPaths.CONFIGDIR.get().resolve(FertiliserForBlockheads.MODID).resolve("botany_pots_compat");
    private static final List<String> SELF_SOIL_BLOCK_PATHS = List.of("fertilized_farmland_rich_healthy", "fertilized_farmland_rich_healthy_stable");
    private static final List<String> FFB_OVERRIDE_SOIL_BLOCK_PATHS = List.of("fertilized_farmland_healthy", "fertilized_farmland_healthy_stable", "fertilized_farmland_rich", "fertilized_farmland_rich_stable", "fertilized_farmland_stable");
    private static volatile long ffbCfgMtimeMillis = Long.MIN_VALUE;
    private static volatile double cachedBonusGrowthChance = 0.5D;
    private static volatile double cachedBonusCropChance = 1.0D;
    private BotanyPotsCompat() {}
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event)
    {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        if (!ModList.get().isLoaded("botanypots")) return;
        try { writeGeneratedPack(); }
        catch (Exception e)
        {
            LOGGER.error("[BotanyPotsCompat] Failed generating datapack; BotanyPots soils/crops will fall back to defaults.", e);
            return;
        }
        event.addRepositorySource(consumer -> {
            Component title = Component.literal("FertiliserForBlockheads: BotanyPots soils/crops (generated)");
            PackLocationInfo location = new PackLocationInfo(GENERATED_PACK_ID, title, PackSource.BUILT_IN, java.util.Optional.empty());
            Pack.ResourcesSupplier resources = new PathPackResources.PathResourcesSupplier(GENERATED_PACK_ROOT);
            PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, false);
            Pack pack = Pack.readMetaAndCreate(location, resources, PackType.SERVER_DATA, selection);
            if (pack != null) consumer.accept(pack);
        });
    }
    private static void writeGeneratedPack() throws Exception
    {
        deleteRecursive();
        Files.createDirectories(GENERATED_PACK_ROOT);
        Files.writeString(GENERATED_PACK_ROOT.resolve("pack.mcmeta"),"{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Generated BotanyPots soil/crop recipes for FertiliserForBlockheads\"\n  }\n}\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        double bonusGrowthChance = getFfbFertiliserBonusGrowthChance();
        double bonusCropChance = getFfbFertiliserBonusCropChance();
        Path selfSoilDir = GENERATED_PACK_ROOT.resolve("data").resolve("botanypots").resolve("recipe").resolve(FertiliserForBlockheads.MODID).resolve("soil");
        Path ffbOverrideSoilDir = GENERATED_PACK_ROOT.resolve("data").resolve("botanypots").resolve("recipe").resolve("farmingforblockheads").resolve("soil");
        Files.createDirectories(selfSoilDir);
        Files.createDirectories(ffbOverrideSoilDir);
        writeDirtSoilTag();
        int soilsWritten = 0;
        for (String blockPath : SELF_SOIL_BLOCK_PATHS)
        {
            double growthModifier = soilGrowthModifier(blockPath, bonusGrowthChance);
            double yieldModifier = soilYieldModifier(blockPath, bonusCropChance);
            soilsWritten += writeSoilRecipe(selfSoilDir, FertiliserForBlockheads.MODID, blockPath, growthModifier, yieldModifier);
        }
        for (String blockPath : FFB_OVERRIDE_SOIL_BLOCK_PATHS)
        {
            double growthModifier = soilGrowthModifier(blockPath, bonusGrowthChance);
            double yieldModifier = soilYieldModifier(blockPath, bonusCropChance);
            soilsWritten += writeSoilRecipe(ffbOverrideSoilDir, "farmingforblockheads", blockPath, growthModifier, yieldModifier);
        }
        LOGGER.info("[BotanyPotsCompat] Wrote {} soil recipe files and updated Botany Pots soil tags at {}", soilsWritten, GENERATED_PACK_ROOT.toAbsolutePath());
    }
    private static int writeSoilRecipe(Path soilDir, String itemNamespace, String blockPath, double growthModifier, double yieldModifier)
    {
        try
        {
            String blockId = itemNamespace + ":" + blockPath;
            JsonObject soil = new JsonObject();
            JsonArray conditions = new JsonArray();
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "bookshelf:block_exists");
            JsonArray values = new JsonArray();
            values.add(blockId);
            condition.add("values", values);
            conditions.add(condition);
            soil.add("bookshelf:load_conditions", conditions);
            soil.addProperty("type", "botanypots:block_derived_soil");
            soil.addProperty("block", blockId);
            if (growthModifier > 0d) soil.addProperty("growth_modifier", growthModifier);
            if (yieldModifier > 0d) soil.addProperty("yield_modifier", yieldModifier);
            try (Writer w = Files.newBufferedWriter(soilDir.resolve(blockPath + ".json"), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
            {
                GSON.toJson(soil, w);
            }
            return 1;
        }
        catch (Exception e)
        {
            LOGGER.warn("[BotanyPotsCompat] Failed writing soil recipe for {}:{}", itemNamespace, blockPath, e);
            return 0;
        }
    }
    private static void writeDirtSoilTag() throws Exception
    {
        Path dirtTag = GENERATED_PACK_ROOT.resolve("data").resolve("botanypots").resolve("tags").resolve("item").resolve("soil").resolve("dirt.json");
        Files.createDirectories(dirtTag.getParent());
        Files.writeString(dirtTag,"{\n  \"values\": [\n    { \"id\": \"fertiliserforblockheads:fertilized_farmland_rich_healthy\", \"required\": false },\n    { \"id\": \"fertiliserforblockheads:fertilized_farmland_rich_healthy_stable\", \"required\": false }\n  ]\n}\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
    }
    private static double soilYieldModifier(String blockPath, double cropBonus)
    {
        return blockPath.contains("rich") ? positiveModifier(cropBonus) : 0.0D;
    }
    private static int writeRichCropRecipes(double bonusCropChance) {
        Path outRecipesRoot = GENERATED_PACK_ROOT.resolve("data").resolve("botanypots").resolve("recipe");
        Path modPath;
        try { modPath = ModList.get().getModFileById("botanypots").getFile().getFilePath(); }
        catch (Exception e) { LOGGER.warn("[BotanyPotsCompat] Could not locate BotanyPots mod file; skipping rich crop generation.", e); return 0; }
        int[] count = new int[]{0};
        FileSystem fs = null;
        try
        {
            Path recipesBase;
            if (Files.isDirectory(modPath)) { recipesBase = modPath.resolve("data").resolve("botanypots").resolve("recipe"); }
            else
            {
                URI uri = URI.create("jar:" + modPath.toUri());
                try { fs = FileSystems.newFileSystem(uri, Map.of()); }
                catch (Exception alreadyOpen) { fs = FileSystems.getFileSystem(uri); }
                recipesBase = fs.getPath("/data/botanypots/recipe");
            }
            if (!Files.exists(recipesBase)) return 0;
            try (var walk = Files.walk(recipesBase))
            {
                walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json") && p.toString().replace('\\', '/').contains("/crop/")).forEach(p -> {
                    try
                    {
                        JsonObject obj = readJsonObject(p);
                        if (obj == null) return;
                        if (!"botanypots:crop".equals(getString(obj))) return;
                        JsonArray categories = obj.getAsJsonArray("categories");
                        if (categories == null || !containsFarmland(categories)) return;
                        Path rel = recipesBase.relativize(p);
                        Path parent = rel.getParent();
                        String fileName = rel.getFileName().toString();
                        if (!fileName.endsWith(".json")) return;
                        if (fileName.endsWith("_rich.json")) return;
                        String richName = fileName.substring(0, fileName.length() - 5) + "_rich.json";
                        JsonObject rich = obj.deepCopy();
                        rich.add("categories", singleRichCategory());
                        String seedItem = getSeedItem(rich);
                        if (seedItem != null) ensureItemExistsCondition(rich, seedItem);
                        if (bonusCropChance > 0d)
                        {
                            JsonArray drops = rich.getAsJsonArray("drops");
                            if (drops != null) rich.add("drops", applyBonusToPrimaryDrop(drops, bonusCropChance));
                        }
                        String parentStr = parent == null ? "" : parent.toString().replace('\\','/');
                        Path outDir = parent == null ? outRecipesRoot : outRecipesRoot.resolve(parentStr);
                        Files.createDirectories(outDir);
                        try (Writer w = Files.newBufferedWriter(outDir.resolve(richName), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) { GSON.toJson(rich, w); }
                        count[0]++;
                    }
                    catch (Exception e) { LOGGER.warn("[BotanyPotsCompat] Failed generating rich crop for {}", p, e); }
                });
            }
        }
        catch (Exception e) { LOGGER.warn("[BotanyPotsCompat] Failed scanning BotanyPots crop recipes; skipping rich crop generation.", e); }
        finally { if (fs != null) try { fs.close(); } catch (Exception ignored) {} }
        return count[0];
    }
    private static JsonObject readJsonObject(Path path)
    {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || !el.isJsonObject()) return null;
            return el.getAsJsonObject();
        }
        catch (Exception e) { return null; }
    }
    private static String getString(JsonObject obj)
    {
        JsonElement el = obj.get("type");
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }
    private static boolean containsFarmland(JsonArray arr)
    {
        for (JsonElement el : arr) if (el != null && el.isJsonPrimitive() && CATEGORY_FARMLAND.equals(el.getAsString())) return true;
        return false;
    }
    private static JsonArray singleRichCategory()
    {
        JsonArray arr = new JsonArray();
        arr.add(CATEGORY_FARMLAND_RICH);
        return arr;
    }
    private static String getSeedItem(JsonObject recipe)
    {
        JsonObject seed = recipe.has("seed") && recipe.get("seed").isJsonObject() ? recipe.getAsJsonObject("seed") : null;
        if (seed == null) return null;
        JsonElement el = seed.get("item");
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }
    private static void ensureItemExistsCondition(JsonObject recipe, String itemId)
    {
        JsonArray conds;
        if (recipe.has("bookshelf:load_conditions") && recipe.get("bookshelf:load_conditions").isJsonArray()) { conds = recipe.getAsJsonArray("bookshelf:load_conditions"); }
        else
        {
            conds = new JsonArray();
            recipe.add("bookshelf:load_conditions", conds);
        }
        JsonObject cond = new JsonObject();
        cond.addProperty("type", "bookshelf:item_exists");
        JsonArray values = new JsonArray();
        values.add(itemId);
        cond.add("values", values);
        conds.add(cond);
    }
    private static JsonArray applyBonusToPrimaryDrop(JsonArray drops, double bonus)
    {
        int guaranteed = (int)Math.floor(bonus);
        double remainder = bonus - guaranteed;
        if (remainder < 0d) remainder = 0d;
        remainder = Math.round(remainder * 1000000d) / 1000000d;
        if (remainder > 0.999999d) { guaranteed += 1; remainder = 0d; }
        JsonArray out = new JsonArray();
        boolean done = false;
        for (int i = 0; i < drops.size(); i++)
        {
            JsonElement el = drops.get(i);
            if (!done && el != null && el.isJsonObject())
            {
                JsonObject d = el.getAsJsonObject();
                double chance = d.has("chance") && d.get("chance").isJsonPrimitive() ? d.get("chance").getAsDouble() : 0d;
                if (chance >= 0.999999d)
                {
                    JsonObject primary = d.deepCopy();
                    int min = getInt(primary, "minRolls", 1);
                    int max = getInt(primary, "maxRolls", min);
                    primary.addProperty("minRolls", Math.max(1, min + guaranteed));
                    primary.addProperty("maxRolls", Math.max(1, max + guaranteed));
                    out.add(primary);
                    if (remainder > 0d)
                    {
                        JsonObject extra = d.deepCopy();
                        extra.addProperty("chance", remainder);
                        extra.addProperty("minRolls", 1);
                        extra.addProperty("maxRolls", 1);
                        out.add(extra);
                    }
                    done = true;
                    continue;
                }
            }
            out.add(el);
        }
        return out;
    }
    private static int getInt(JsonObject obj, String key, int def)
    {
        JsonElement el = obj.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsInt() : def;
    }
    private static double soilGrowthModifier(String blockPath, double growthBonus)
    {
        return blockPath.contains("healthy") ? positiveModifier(growthBonus) : 0.0D;
    }
    private static double positiveModifier(double value)
    {
        return Math.max(value, 0.0D);
    }
    private static double getFfbFertiliserBonusGrowthChance()
    {
        reloadFfbConfigIfNeeded();
        return cachedBonusGrowthChance;
    }
    private static double getFfbFertiliserBonusCropChance() {
        reloadFfbConfigIfNeeded();
        return cachedBonusCropChance;
    }
    private static synchronized void reloadFfbConfigIfNeeded()
    {
        Path cfgPath = FMLPaths.CONFIGDIR.get().resolve("farmingforblockheads-common.toml");
        long mtime = Long.MIN_VALUE;
        try { if (Files.exists(cfgPath)) mtime = Files.getLastModifiedTime(cfgPath).toMillis(); }
        catch (Exception ignored) {}
        if (mtime == ffbCfgMtimeMillis) return;
        ffbCfgMtimeMillis = mtime;
        double growthFallback = 0.5D;
        double cropFallback = 1.0D;
        if (!Files.exists(cfgPath))
        {
            cachedBonusGrowthChance = growthFallback;
            cachedBonusCropChance = cropFallback;
            return;
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(cfgPath).sync().preserveInsertionOrder().build())
        {
            config.load();
            cachedBonusGrowthChance = readDouble(config, "fertilizerBonusGrowthChance", growthFallback);
            cachedBonusCropChance = readDouble(config, "fertilizerBonusCropChance", cropFallback);
        }
        catch (Exception e)
        {
            LOGGER.warn("[BotanyPotsCompat] Failed reading {}", cfgPath.toAbsolutePath(), e);
            cachedBonusGrowthChance = growthFallback;
            cachedBonusCropChance = cropFallback;
        }
    }
    private static double readDouble(CommentedFileConfig config, String key, double defaultValue)
    {
        Object value = config.get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) { try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {} }
        return defaultValue;
    }
    private static void deleteRecursive()
    {
        if (!Files.exists(GENERATED_PACK_ROOT)) return;
        try (var walk = Files.walk(GENERATED_PACK_ROOT)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); }
        catch (Exception e) { LOGGER.warn("[BotanyPotsCompat] Failed cleaning generated pack directory: {}", GENERATED_PACK_ROOT.toAbsolutePath(), e); }
    }
}