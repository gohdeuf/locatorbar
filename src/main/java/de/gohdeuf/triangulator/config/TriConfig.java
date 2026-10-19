package de.gohdeuf.triangulator.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Kleine JSON-Config (keine externe Config-Library noetig).
 */
public final class TriConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TriConfig instance;

    public boolean hudEnabled = true;
    public int maxSamplesPerTarget = 64;
    public int minSamplesForEstimate = 2;
    /** Messwerte aelter als sampleTTLMillis werden verworfen. */
    public long sampleTtlMillis = 60_000;
    /** Nur alle N Client-Ticks neu triangulieren. */
    public int recalcIntervalTicks = 10;

    public static TriConfig get() {
        if (instance == null) instance = new TriConfig();
        return instance;
    }

    public static void load() {
        Path path = path();
        if (!Files.exists(path)) {
            instance = new TriConfig();
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(path)) {
            instance = GSON.fromJson(r, TriConfig.class);
        } catch (IOException e) {
            instance = new TriConfig();
        }
        if (instance == null) instance = new TriConfig();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(get(), w);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("triangulator.json");
    }
}
