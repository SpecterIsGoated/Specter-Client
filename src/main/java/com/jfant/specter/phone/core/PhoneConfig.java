package com.jfant.specter.phone.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The phone's own preferences ({@code specter/phone/config.json}): theme, wallpaper, dock, notification choices.
 * Position, scale and the chat-key behaviour are settings of the Donut Phone module instead, so they show up in the
 * Specter GUI too. Saved atomically a moment after a change.
 */
public final class PhoneConfig {
   public static final Path DIR = SpecterCore.FOLDER.toPath().resolve("phone");
   private static final Path FILE = DIR.resolve("config.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public String theme = "midnight";
   /** Null = the theme's own wallpaper. */
   public String wallpaper;
   public List<String> dock = new ArrayList<>(List.of("settings", "chat"));
   public List<String> hidden = new ArrayList<>();
   public Map<String, NotifyPrefs> notify = new HashMap<>();
   /** Banners slide in at the top of the phone screen. */
   public boolean banners = true;
   /** While the phone is put away, notifications also pop up as Specter toasts. */
   public boolean toastsWhenClosed = true;
   public boolean firstRunDone;
   /** Show the lock screen (time and notifications) each time the phone comes out. */
   public boolean lockScreen = true;
   public boolean use24h = true;

   public static final class NotifyPrefs {
      public boolean enabled = true;
      public boolean sound = true;
   }

   private transient long dirtyAt;

   public NotifyPrefs prefs(String appId) {
      return this.notify.computeIfAbsent(appId, k -> new NotifyPrefs());
   }

   public static PhoneConfig load() {
      try {
         if (Files.isRegularFile(FILE)) {
            PhoneConfig c = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), PhoneConfig.class);
            if (c != null) {
               if (c.dock == null) c.dock = new ArrayList<>();
               // 2026-11-22: the dock became just Settings and Chat (DonutGram, Maps and Games live in the grid)
               if (c.dock.equals(List.of("donutgram", "maps", "games", "chat"))) {
                  c.dock = new ArrayList<>(List.of("settings", "chat"));
                  c.markDirty();
               }
               if (c.hidden == null) c.hidden = new ArrayList<>();
               if (c.notify == null) c.notify = new HashMap<>();
               return c;
            }
         }
      } catch (Exception e) {
         SpecterClientAddon.LOG.warn("Donut Phone: could not read {}, using defaults", FILE, e);
      }
      return new PhoneConfig();
   }

   public void markDirty() {
      this.dirtyAt = System.currentTimeMillis();
   }

   /** Called every tick: writes the file 1.5 s after the last change. */
   public void tick() {
      if (this.dirtyAt != 0 && System.currentTimeMillis() - this.dirtyAt > 1500) {
         this.saveNow();
      }
   }

   public void saveNow() {
      this.dirtyAt = 0;
      writeAtomic(FILE, GSON.toJson(this));
   }

   // ---------------------------------------------------------------------------------------------- shared file helpers

   public static void writeAtomic(Path file, String text) {
      try {
         Files.createDirectories(file.getParent());
         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
         Files.writeString(tmp, text, StandardCharsets.UTF_8);
         try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException e) {
         SpecterClientAddon.LOG.warn("Donut Phone: could not save {}", file, e);
      }
   }

   public static JsonObject readJson(Path file) {
      try {
         if (Files.isRegularFile(file)) {
            var e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (e.isJsonObject()) return e.getAsJsonObject();
         }
      } catch (Exception e) {
         SpecterClientAddon.LOG.warn("Donut Phone: could not read {}", file, e);
      }
      return new JsonObject();
   }

   public static String pretty(JsonObject o) {
      return GSON.toJson(o);
   }
}
