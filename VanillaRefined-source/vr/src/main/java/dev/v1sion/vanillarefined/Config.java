package dev.v1sion.vanillarefined;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/vanillarefined.json — every feature can be switched off. */
public final class Config {
    // world & blocks
    public boolean animatedContainers = true;
    public boolean doubleDoors = true;
    public boolean fallingBlockDust = true;
    public boolean flickeringLights = true;
    // items
    public boolean itemFrameEffects = true;
    // player
    public boolean coldBreath = true;
    public boolean ladderCreak = true;
    // sound
    public boolean ambientSounds = true;
    public float ambientVolume = 0.6f;
    public boolean wetFootsteps = true;
    // mobs
    public boolean deathEffects = true;
    public boolean villagerEmotes = true;
    // farming
    public boolean rightClickHarvest = true;
    public boolean cropSparkle = true;
    public boolean boneMealPop = true;
    public boolean animalsEatDroppedFood = true;
    public boolean sheepRegrowFx = true;
    // hud
    public boolean hotbarPolish = true;
    public boolean infoHud = true;
    public boolean armorHud = true;
    public boolean toasts = true;
    public boolean betterDeathScreen = true;

    public static Config I = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load(Path dir) {
        Path f = dir.resolve("vanillarefined.json");
        try {
            if (Files.exists(f)) I = GSON.fromJson(Files.readString(f), Config.class);
            if (I == null) I = new Config();
            Files.writeString(f, GSON.toJson(I));
        } catch (Exception e) { VanillaRefined.LOG.error("Bad config, using defaults", e); I = new Config(); }
    }
}
