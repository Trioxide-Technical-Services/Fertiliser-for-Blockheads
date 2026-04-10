package com.goldmike.fertiliserforblockheads;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.nio.file.Path;
public final class FarmingForBlockheadsConfigBridge
{
    public static double bonusGrowthChance = 1.0; // red/healthy
    public static double bonusCropChance = 1.0;   // green/rich
    public static double regressionChance = 0.0;
    public static void load()
    {
        Path path = FMLPaths.CONFIGDIR.get().resolve("farmingforblockheads-common.toml");
        if (!Files.exists(path)) { return; }
        try (CommentedFileConfig cfg = CommentedFileConfig.builder(path).sync().build())
        {
            cfg.load();
            bonusGrowthChance = getDouble(cfg, "fertilizerBonusGrowthChance", 1.0);
            bonusCropChance   = getDouble(cfg, "fertilizerBonusCropChance",   1.0);
            regressionChance  = clamp01(getDouble(cfg, "fertilizerRegressionChance", 0.0));
        }
    }
    private static double getDouble(CommentedFileConfig cfg, String key, double def)
    {
        Object v = cfg.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }
    private static double clamp01(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }
}