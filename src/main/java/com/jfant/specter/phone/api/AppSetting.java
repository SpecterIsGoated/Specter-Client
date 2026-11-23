package com.jfant.specter.phone.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.function.Function;

/**
 * One setting an app exposes in Settings &gt; Apps &gt; (app). Values live in the app's own data file, so an app
 * reads them with {@link #get(PhoneContext)} and never deals with storage. Three kinds cover nearly everything:
 * {@link #toggle}, {@link #choice} and {@link #number}.
 */
public final class AppSetting<T> {
   public enum Kind {
      TOGGLE,
      CHOICE,
      NUMBER
   }

   public final Kind kind;
   public final String key;
   public final String title;
   public final String description;
   public final T defaultValue;
   public final List<String> options;
   public final double min;
   public final double max;
   public final double step;
   private final Function<JsonElement, T> read;

   private AppSetting(Kind kind, String key, String title, String description, T defaultValue, List<String> options, double min, double max,
                      double step, Function<JsonElement, T> read) {
      this.kind = kind;
      this.key = key;
      this.title = title;
      this.description = description;
      this.defaultValue = defaultValue;
      this.options = options;
      this.min = min;
      this.max = max;
      this.step = step;
      this.read = read;
   }

   public static AppSetting<Boolean> toggle(String key, String title, String description, boolean defaultValue) {
      return new AppSetting<>(Kind.TOGGLE, key, title, description, defaultValue, List.of(), 0, 1, 1, JsonElement::getAsBoolean);
   }

   public static AppSetting<String> choice(String key, String title, String description, String defaultValue, List<String> options) {
      return new AppSetting<>(Kind.CHOICE, key, title, description, defaultValue, List.copyOf(options), 0, options.size() - 1, 1, e -> {
         String v = e.getAsString();
         return options.contains(v) ? v : defaultValue;
      });
   }

   public static AppSetting<Double> number(String key, String title, String description, double defaultValue, double min, double max, double step) {
      return new AppSetting<>(Kind.NUMBER, key, title, description, defaultValue, List.of(), min, max, step,
         e -> Math.clamp(e.getAsDouble(), min, max));
   }

   public T get(PhoneContext phone) {
      JsonElement e = phone.storage().getAsJsonObject("settings") == null ? null : phone.storage().getAsJsonObject("settings").get(this.key);
      if (e == null || e.isJsonNull()) return this.defaultValue;
      try {
         return this.read.apply(e);
      } catch (RuntimeException ex) {
         return this.defaultValue;
      }
   }

   public void set(PhoneContext phone, T value) {
      com.google.gson.JsonObject root = phone.storage();
      com.google.gson.JsonObject settings = root.getAsJsonObject("settings");
      if (settings == null) {
         settings = new com.google.gson.JsonObject();
         root.add("settings", settings);
      }
      if (value instanceof Boolean b) settings.add(this.key, new JsonPrimitive(b));
      else if (value instanceof Number n) settings.add(this.key, new JsonPrimitive(n));
      else settings.add(this.key, new JsonPrimitive(String.valueOf(value)));
      phone.saveStorage();
   }
}
