package dev.maptooltip.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.map.MapState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Owns every replacement rule. Everything lives in config/map-tooltip/:
 *   replacements.json  - the list of rules (+ global on/off switch)
 *   images/<id>.bin    - the 16384 converted map colour bytes for each rule
 */
public final class ReplacementManager {
    private static final Logger LOG = LoggerFactory.getLogger("map-tooltip");
    private static final ReplacementManager INSTANCE = new ReplacementManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("map-tooltip");
    private final Path storeFile = dir.resolve("replacements.json");
    private final Path imageDir = dir.resolve("images");

    private final List<Replacement> entries = new ArrayList<>();
    private final Map<String, byte[]> colorCache = new HashMap<>();
    private final Map<MapState, byte[]> originals = new WeakHashMap<>();
    private boolean globalEnabled = true;

    private ReplacementManager() {}

    public static ReplacementManager get() {
        return INSTANCE;
    }

    /** Persisted JSON shape. */
    private static final class StoreFile {
        boolean globalEnabled = true;
        List<Replacement> entries = new ArrayList<>();
    }

    public void load() {
        try {
            Files.createDirectories(imageDir);
            if (Files.exists(storeFile)) {
                StoreFile data = GSON.fromJson(Files.readString(storeFile), StoreFile.class);
                if (data != null) {
                    globalEnabled = data.globalEnabled;
                    if (data.entries != null) entries.addAll(data.entries);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("Map Tooltip: could not load replacements.json", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(dir);
            StoreFile data = new StoreFile();
            data.globalEnabled = globalEnabled;
            data.entries = entries;
            Files.writeString(storeFile, GSON.toJson(data));
        } catch (IOException e) {
            LOG.error("Map Tooltip: could not save replacements.json", e);
        }
    }

    public List<Replacement> all() {
        return List.copyOf(entries);
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean value) {
        globalEnabled = value;
        save();
    }

    public Optional<Replacement> findById(String id) {
        return entries.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public Optional<Replacement> findByMapId(int mapId) {
        return entries.stream().filter(r -> r.mapId() == mapId).findFirst();
    }

    /**
     * Adds or updates a rule. Only one rule may target a given map ID, so any other
     * rule for the same map ID is replaced.
     */
    public void upsert(Replacement rule, byte[] colors) throws IOException {
        Files.createDirectories(imageDir);
        Files.write(imageDir.resolve(rule.id() + ".bin"), colors);
        colorCache.put(rule.id(), colors);

        entries.removeIf(r -> r.mapId() == rule.mapId() && !r.id().equals(rule.id()));
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(rule.id())) {
                index = i;
                break;
            }
        }
        if (index >= 0) entries.set(index, rule);
        else entries.add(rule);
        save();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public void remove(String id) {
        entries.removeIf(r -> r.id().equals(id));
        colorCache.remove(id);
        try {
            Files.deleteIfExists(imageDir.resolve(id + ".bin"));
        } catch (IOException e) {
            LOG.warn("Map Tooltip: could not delete image data for {}", id, e);
        }
        save();
    }

    public void setEnabled(String id, boolean enabled) {
        findById(id).ifPresent(r -> r.setEnabled(enabled));
        save();
    }

    /** Returns the replacement colours for a map ID if one should be shown, otherwise null. */
    public byte[] colorsFor(int mapId) {
        if (!globalEnabled) return null;
        Optional<Replacement> rule = findByMapId(mapId);
        if (rule.isEmpty() || !rule.get().enabled()) return null;
        return colorsOf(rule.get().id());
    }

    /**
     * Called every time a map is drawn. Swaps the map's pixels for the replacement when one
     * applies, and restores the original pixels when it doesn't. The originals are kept in a
     * weak map so nothing leaks when a map is unloaded.
     */
    public void applyTo(int mapId, MapState state, String hook) {
        byte[] colors = state.colors;
        if (colors == null) return;

        byte[] replacement = colorsFor(mapId);
        byte[] saved = originals.get(state);

        if (replacement != null && replacement.length == colors.length) {
            if (saved == null) {
                saved = colors.clone();
                originals.put(state, saved);
                LOG.info("Map Tooltip: replacing map #{} (hook: {})", mapId, hook);
            }
            System.arraycopy(replacement, 0, colors, 0, colors.length);
        } else if (saved != null) {
            System.arraycopy(saved, 0, colors, 0, colors.length);
            originals.remove(state);
        }
    }

    /** Stored pixels for a rule regardless of on/off state, used for previews. */
    public byte[] imageFor(String id) {
        return colorsOf(id);
    }

    private byte[] colorsOf(String id) {
        byte[] cached = colorCache.get(id);
        if (cached != null) return cached;
        try {
            byte[] data = Files.readAllBytes(imageDir.resolve(id + ".bin"));
            if (data.length == ImageConverter.SIZE * ImageConverter.SIZE) {
                colorCache.put(id, data);
                return data;
            }
        } catch (IOException e) {
            LOG.warn("Map Tooltip: missing image data for rule {}", id);
        }
        return null;
    }
}
