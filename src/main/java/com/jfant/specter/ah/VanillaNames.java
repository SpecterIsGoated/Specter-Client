package com.jfant.specter.ah;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;

public final class VanillaNames {
   private static final String LANG = "lang/en_us.json";
   private static Map<String, String> names;

   private VanillaNames() {
   }

   public static String of(Item item) {
      String name = load().get(item.getDescriptionId());
      return name != null && !name.isBlank() ? name : fromId(item);
   }

   public static String search(Item item) {
      return of(item).toLowerCase(Locale.ROOT);
   }

   static String fromId(Item item) {
      String path = BuiltInRegistries.ITEM.getKey(item).getPath();
      StringBuilder out = new StringBuilder(path.length());
      boolean upper = true;

      for (char c : path.toCharArray()) {
         if (c == '_') {
            out.append(' ');
            upper = true;
         } else {
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
         }
      }

      return out.toString();
   }

   private static synchronized Map<String, String> load() {
      if (names != null) {
         return names;
      } else {
         Map<String, String> out = new HashMap<>();

         try (InputStream in = open()) {
            if (in != null) {
               out.putAll(parse(in));
            }
         } catch (RuntimeException | IOException var6) {
            SpecterClientAddon.LOG.warn("Could not read the vanilla language file - using item ids for names", var6);
         }

         if (out.isEmpty()) {
            SpecterClientAddon.LOG.warn("Vanilla language file not found - using item ids for names");
         }

         names = out;
         return names;
      }
   }

   private static InputStream open() throws IOException {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            IoSupplier<InputStream> supplier = mc.getVanillaPackResources()
               .getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace("lang/en_us.json"));
            if (supplier != null) {
               return supplier.get();
            }
         }
      } catch (RuntimeException | LinkageError var2) {
      }

      return VanillaNames.class.getResourceAsStream("/assets/minecraft/lang/en_us.json");
   }

   static Map<String, String> parse(InputStream in) throws IOException {
      Map<String, String> out = new HashMap<>();

      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

         for (Entry<String, JsonElement> e : root.entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
               out.put(e.getKey(), e.getValue().getAsString());
            }
         }
      }

      return out;
   }
}
