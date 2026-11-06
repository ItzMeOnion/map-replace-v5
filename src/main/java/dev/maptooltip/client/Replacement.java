package dev.maptooltip.client;

/** One "map ID -> image" rule. Stored as JSON; the converted pixels live in a sibling .bin file. */
public final class Replacement {
    private String id;
    private int mapId;
    private String name;
    private String sourcePath;
    private boolean enabled;

    /** Used by Gson. */
    public Replacement() {}

    public Replacement(String id, int mapId, String name, String sourcePath, boolean enabled) {
        this.id = id;
        this.mapId = mapId;
        this.name = name;
        this.sourcePath = sourcePath;
        this.enabled = enabled;
    }

    public String id() { return id; }
    public int mapId() { return mapId; }
    public String name() { return name; }
    public String sourcePath() { return sourcePath; }
    public boolean enabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
