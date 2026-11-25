package com.jfant.specter.names;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.network.Http;
import com.jfant.specter.core.utils.network.MeteorExecutor;
import com.mojang.util.UndashedUuid;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.jfant.specter.core.SpecterCore.mc;

/**
 * The player behind an id a server stamps on something - the owner id on an item in a frame, say. DonutSMP writes the
 * owner's UUID, which tells you nothing to look at, so it is turned into a name.
 *
 * <p>Three places, cheapest first: the tab list, which costs nothing and is right even for a name changed an hour ago;
 * the names looked up before, kept in {@code .minecraft/specter/names.csv} so a base full of frames is only ever looked
 * up once; and failing both, Mojang's public profile endpoint - the same place the game itself asks when it draws a
 * player head, one id at a time off the render thread. Only the id goes out, nothing of yours.
 *
 * <p>{@link #label} is what a label shows: the name once it is known, and a short form of the id until then, so it fills
 * in a moment after the frame comes into range instead of flashing empty.
 *
 * <p>Every outcome is written to the game log once per id - named, no such player, could not ask, not a player id at all
 * - so a label that stays an id says in the log why.
 */
public final class OwnerNames {
    private OwnerNames() {}

    /** Names known now, by id. */
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();
    /** Ids Mojang has no player for: never asked again. */
    private static final Set<UUID> noName = ConcurrentHashMap.newKeySet();
    /** Ids queued or being asked about now, so the same one is not queued twice. */
    private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    /** When asking last failed for a reason that may pass - no connection, rate limited, Mojang down. */
    private static final Map<UUID, Long> failedAt = new ConcurrentHashMap<>();
    /** Values that are not a player id at all, so each is only complained about once. */
    private static final Set<String> notIds = ConcurrentHashMap.newKeySet();

    private static final Queue<UUID> queue = new ConcurrentLinkedQueue<>();
    /** One lookup at a time, with a gap: Mojang throttles a burst, and there is no hurry. */
    private static final long GAP_MS = 150;
    /** How long after a failure that may pass before the same id is asked about again. */
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private static final Gson GSON = new Gson();
    /** What Mojang answers for an id it has no player for: no content at all, which is not an error. */
    private static final int NO_CONTENT = 204;

    private static volatile boolean working;
    private static volatile boolean loaded;

    /** The name for {@code id}, or null while it is not known; a lookup is started for an id that is not. */
    public static String name(String id) {
        UUID uuid = parse(id);
        if (uuid == null) return null;

        String known = names.get(uuid);
        if (known != null) return known;

        // The tab list: free, and the only place that has a name changed since it was last looked up.
        ClientPacketListener handler = mc.getConnection();
        if (handler != null) {
            PlayerInfo entry = handler.getPlayerInfo(uuid);
            String listed = entry == null || entry.getProfile() == null ? null : entry.getProfile().name();
            if (listed != null && !listed.isBlank()) {
                remember(uuid, listed, "the tab list");
                return listed;
            }
        }

        ask(uuid);
        return null;
    }

    /**
     * What to write on a label for {@code id}: its name, or a short form of the id until that is known. Anything that is
     * not an id at all - a server writing the name itself - is given back as it is.
     */
    public static String label(String id) {
        if (id == null || id.isBlank()) return null;
        String name = name(id);
        if (name != null) return name;

        UUID uuid = parse(id);
        if (uuid == null) return id;
        return UndashedUuid.toString(uuid).substring(0, 8) + "...";
    }

    /**
     * Starts the lookup for an id a label has not been asked for yet, so it is ready before it is looked at. Something
     * that is not an id at all is said once in the log: on another server that key may hold anything.
     */
    public static void prefetch(String id) {
        if (id != null && !id.isBlank() && parse(id) == null) {
            if (notIds.add(id)) SpecterCore.LOG.info("Specter: \"{}\" is not a player id, so there is no name to look up - shown as it is", id);
            return;
        }
        name(id);
    }

    /** Dashed or plain hex, either way; null when it is not an id. */
    public static UUID parse(String id) {
        if (id == null) return null;
        String trimmed = id.trim();
        try {
            if (trimmed.length() == 36) return UUID.fromString(trimmed);
            if (trimmed.length() == 32) return UndashedUuid.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static void remember(UUID id, String name, String from) {
        if (names.put(id, name) == null) {
            failedAt.remove(id);
            SpecterCore.LOG.info("Specter: owner {} is {} (from {})", UndashedUuid.toString(id), name, from);
            MeteorExecutor.execute(() -> append(id, name));
        }
    }

    private static void ask(UUID id) {
        if (noName.contains(id) || names.containsKey(id)) return;

        Long failed = failedAt.get(id);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) return;

        if (!pending.add(id)) return;
        queue.add(id);
        start();
    }

    private static synchronized void start() {
        if (working) return;
        working = true;
        MeteorExecutor.execute(OwnerNames::work);
    }

    private static void work() {
        try {
            if (!loaded) load();
            UUID id;
            while ((id = queue.poll()) != null) {
                try {
                    if (!names.containsKey(id)) look(id);
                } finally {
                    pending.remove(id);
                }
                if (!queue.isEmpty()) Thread.sleep(GAP_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            SpecterCore.LOG.warn("Specter: looking up an owner name failed", e);
        } finally {
            working = false;
            // Something queued while this was winding down: pick it up rather than leave it sitting there.
            if (!queue.isEmpty()) start();
        }
    }

    /**
     * Asks Mojang about one id. Its profile endpoint answers an id it does not know with 204 and an empty body, which
     * is why the status is read here instead of handing the body straight to a JSON parser - that threw, and every
     * failure then looked the same as a server being down.
     */
    private static void look(UUID id) {
        String plain = UndashedUuid.toString(id);
        HttpResponse<String> res = Http.get("https://sessionserver.mojang.com/session/minecraft/profile/" + plain)
            .exceptionHandler(e -> SpecterCore.LOG.info("Specter: could not reach Mojang to name owner {} ({})", plain, e.getMessage()))
            .sendStringResponse();

        int code = res.statusCode();
        String body = res.body();

        if (code == 200 && body != null && !body.isBlank()) {
            try {
                Profile profile = GSON.fromJson(body, Profile.class);
                if (profile != null && profile.name != null && !profile.name.isBlank()) {
                    remember(id, profile.name, "Mojang");
                    return;
                }
            } catch (JsonSyntaxException e) {
                SpecterCore.LOG.info("Specter: Mojang's answer for owner {} was not a profile", plain);
            }
            // A 200 with no name in it is Mojang's business, not something waiting will fix.
            noName.add(id);
            return;
        }

        // No such player: an id made up by the server (DonutSMP's own gownerctid is one), or an account that never
        // existed on Mojang. Written down as having no name, so it is not asked about again in a later session either.
        if (code == NO_CONTENT || code == Http.NOT_FOUND) {
            if (noName.add(id)) {
                SpecterCore.LOG.info("Specter: Mojang has no player for owner {} - the label keeps the id", plain);
                append(id, "");
            }
            return;
        }

        // Rate limited, offline, or Mojang having a moment: worth asking again later.
        failedAt.put(id, System.currentTimeMillis());
        SpecterCore.LOG.info("Specter: Mojang answered {} for owner {} - trying again later", code, plain);
    }

    private static final class Profile {
        String name;
    }

    // ---- the names looked up before ----

    /** {@code .minecraft/specter/names.csv}: one {@code <plain id>,<name>} a line. */
    public static File file() {
        return new File(SpecterCore.FOLDER, "names.csv");
    }

    private static void load() {
        loaded = true;
        readNames();
        readUserCache();
    }

    /**
     * The game's own {@code usercache.json}: names it has already resolved, in the game folder. Free, offline, and it
     * often has the players round a base already, so Mojang is never asked for those.
     */
    private static void readUserCache() {
        File file = FabricLoader.getInstance().getGameDir().resolve("usercache.json").toFile();
        if (!file.isFile()) return;
        int read = 0;
        try {
            CachedName[] cached = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), CachedName[].class);
            if (cached == null) return;
            for (CachedName entry : cached) {
                if (entry == null || entry.name == null || entry.name.isBlank()) continue;
                UUID id = parse(entry.uuid);
                if (id != null && names.putIfAbsent(id, entry.name) == null) read++;
            }
            if (read > 0) SpecterCore.LOG.info("Specter: {} name(s) read from the game's usercache.json", read);
        } catch (IOException | RuntimeException e) {
            SpecterCore.LOG.info("Specter: could not read usercache.json ({})", e.getMessage());
        }
    }

    private static final class CachedName {
        String uuid;
        String name;
    }

    private static void readNames() {
        File file = file();
        if (!file.isFile()) return;
        int read = 0;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                int comma = line.indexOf(',');
                if (comma <= 0) continue;
                UUID id = parse(line.substring(0, comma));
                if (id == null) continue;
                String name = line.substring(comma + 1).trim();
                // No name written down: Mojang has no player for that id, and saying so saves asking again.
                if (name.isEmpty()) noName.add(id);
                else if (names.putIfAbsent(id, name) == null) read++;
            }
            if (read > 0) SpecterCore.LOG.info("Specter: {} owner name(s) read from {}", read, file.getName());
        } catch (IOException | RuntimeException e) {
            SpecterCore.LOG.warn("Specter: could not read {}", file.getName(), e);
        }
    }

    private static synchronized void append(UUID id, String name) {
        // A name with a comma or a newline in it is no name at all - it would only break the next read.
        if (name.indexOf(',') >= 0 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) return;
        try {
            File file = file();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), UndashedUuid.toString(id) + ',' + name + '\n', StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            SpecterCore.LOG.warn("Specter: could not save the owner name for {}", name, e);
        }
    }
}
