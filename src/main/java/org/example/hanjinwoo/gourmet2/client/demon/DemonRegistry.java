package org.example.hanjinwoo.gourmet2.client.demon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.example.hanjinwoo.gourmet2.Gourmet2;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Appetite Demon and the colour of its Gourmet Cells, loaded from
 * {@code assets/gourmet2/demons/demons.json} (resource packs may override it). The cell colour
 * picks which effect files play for that demon's Intimidation and Ki Release; add a demon by adding
 * an entry there plus its {@code toriko/<kind>_<cell_color>.efkefc} files.
 */
public final class DemonRegistry implements ResourceManagerReloadListener {
    private static final ResourceLocation FILE = Gourmet2.id("demons/demons.json");
    private static final Map<String, Demon> DEMONS = new LinkedHashMap<>();
    private static String defaultId = "";

    public record Demon(String id, String cellColor, int rgb, Map<String, String> effects) {
        /**
         * Effect paths to try for {@code kind} (e.g. "intimidation", "ki"), best match first: the
         * explicitly named file, then the cell-colour file, then the colourless default.
         */
        public List<String> effectCandidates(String kind) {
            List<String> paths = new ArrayList<>();
            String explicit = effects.get(kind);
            if (explicit != null) {
                paths.add(explicit);
            }
            paths.add("toriko/" + kind + "_" + cellColor);
            paths.add("toriko/" + kind);
            if (kind.equals("ki")) {
                // No dedicated ki file yet: the intimidation aura stands in for it.
                paths.addAll(effectCandidates("intimidation"));
            }
            return paths;
        }
    }

    /** The demon used when nothing else picks one (a per-player choice can plug in here later). */
    public static Demon active() {
        Demon demon = DEMONS.get(defaultId);
        if (demon != null) {
            return demon;
        }
        return DEMONS.values().stream().findFirst()
                .orElse(new Demon("none", "red", 0xD42A2A, Map.of()));
    }

    public static Demon byId(String id) {
        return DEMONS.getOrDefault(id, active());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        DEMONS.clear();
        defaultId = "";
        var resource = manager.getResource(FILE);
        if (resource.isEmpty()) {
            return;
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("default")) {
                defaultId = root.get("default").getAsString();
            }
            JsonObject demons = root.getAsJsonObject("demons");
            if (demons == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : demons.entrySet()) {
                JsonObject json = entry.getValue().getAsJsonObject();
                String color = json.has("cell_color") ? json.get("cell_color").getAsString() : "red";
                int rgb = json.has("rgb") ? parseRgb(json.get("rgb").getAsString()) : 0xFFFFFF;
                Map<String, String> effects = new LinkedHashMap<>();
                if (json.has("effects")) {
                    for (Map.Entry<String, JsonElement> effect : json.getAsJsonObject("effects").entrySet()) {
                        effects.put(effect.getKey(), effect.getValue().getAsString());
                    }
                }
                DEMONS.put(entry.getKey(), new Demon(entry.getKey(), color, rgb, effects));
            }
        } catch (Exception e) {
            Gourmet2.LOGGER.error("Could not read {}", FILE, e);
        }
    }

    private static int parseRgb(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }
}
