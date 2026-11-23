package com.jfant.specter.phone.apps.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.render.color.SettingColor;

/**
 * What a destination's recipe may do: change a setting of a Specter module, or switch a module on. Every change is
 * recorded (old value as text, which Specter settings can parse back) so it can be undone - also after a restart,
 * since the record is saved with the app's data.
 */
public final class TravelContext {
   private final JsonArray undo = new JsonArray();

   /** Sets {@code module}'s setting {@code name}. Values: Boolean, Number, an enum constant's name, or an ARGB int for colours. */
   public TravelContext set(Class<? extends Module> type, String name, Object value) {
      Module module = Modules.get().get(type);
      if (module == null) return this;
      Setting<?> setting = module.settings.get(name);
      if (setting == null) {
         SpecterClientAddon.LOG.warn("DonutTravel: {} has no setting {}", module.name, name);
         return this;
      }
      String before = setting.toString();
      if (apply(setting, value)) {
         JsonObject o = new JsonObject();
         o.addProperty("module", module.name);
         o.addProperty("setting", name);
         o.addProperty("value", before);
         this.undo.add(o);
      }
      return this;
   }

   public TravelContext turnOn(Class<? extends Module> type) {
      Module module = Modules.get().get(type);
      if (module != null && !module.isActive()) {
         module.toggle();
         JsonObject o = new JsonObject();
         o.addProperty("module", module.name);
         o.addProperty("off", true);
         this.undo.add(o);
      }
      return this;
   }

   JsonArray record() {
      return this.undo;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private static boolean apply(Setting<?> setting, Object value) {
      Object current = setting.get();
      Object v = value;
      if (current instanceof SettingColor && value instanceof Integer argb) {
         v = new SettingColor(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
      } else if (current instanceof Double && value instanceof Number n) {
         v = n.doubleValue();
      } else if (current instanceof Integer && value instanceof Number n) {
         v = n.intValue();
      } else if (current instanceof Enum<?> e && value instanceof String s) {
         v = null;
         for (Object c : e.getDeclaringClass().getEnumConstants()) if (((Enum<?>)c).name().equalsIgnoreCase(s)) v = c;
         if (v == null) return false;
      }
      if (v == null || !current.getClass().isInstance(v) && !(current instanceof Enum && v instanceof Enum)) return false;
      if (current.equals(v)) return false;
      return ((Setting)setting).set(v);
   }

   /** Undoes a saved record, newest change first. */
   static void undo(JsonArray record) {
      for (int i = record.size() - 1; i >= 0; i--) {
         try {
            JsonObject o = record.get(i).getAsJsonObject();
            Module module = Modules.get().get(o.get("module").getAsString());
            if (module == null) continue;
            if (o.has("off")) {
               if (module.isActive()) module.toggle();
               continue;
            }
            Setting<?> setting = module.settings.get(o.get("setting").getAsString());
            if (setting != null) setting.parse(o.get("value").getAsString());
         } catch (RuntimeException e) {
            SpecterClientAddon.LOG.warn("DonutTravel: could not undo a change", e);
         }
      }
   }

   static boolean isRecord(JsonElement e) {
      return e != null && e.isJsonArray();
   }
}
