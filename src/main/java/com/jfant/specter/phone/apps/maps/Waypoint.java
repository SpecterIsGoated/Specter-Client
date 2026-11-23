package com.jfant.specter.phone.apps.maps;

import com.google.gson.JsonObject;
import com.jfant.specter.phone.ui.Glyph;

/** A saved place. {@code dim} is the dimension id path (overworld, the_nether, the_end, ...). */
public final class Waypoint {
   public enum Category {
      HOME("Home", Glyph.HOME, 0xFF60A5FA),
      BASE("Base", Glyph.FLAG, 0xFFA78BFA),
      FARM("Farm", Glyph.SPARK, 0xFF4ADE80),
      SHOP("Shop", Glyph.STAR, 0xFFFBBF24),
      PORTAL("Portal", Glyph.CROSSHAIR, 0xFFC084FC),
      SPAWNER("Spawner", Glyph.INFO, 0xFFF87171),
      OTHER("Other", Glyph.PIN, 0xFF94A3B8);

      public final String title;
      public final Glyph glyph;
      public final int color;

      Category(String title, Glyph glyph, int color) {
         this.title = title;
         this.glyph = glyph;
         this.color = color;
      }
   }

   public final String id;
   public String name;
   public final int x;
   public final int y;
   public final int z;
   public final String dim;
   public Category category;
   public boolean favorite;
   public final long created;
   /** Who shared it (null for your own). */
   public String from;
   /** The server home this is ({@code /home <this>}), or null for an ordinary place. */
   public String home;

   public Waypoint(String id, String name, int x, int y, int z, String dim, Category category, boolean favorite, long created, String from) {
      this.id = id;
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
      this.dim = dim;
      this.category = category;
      this.favorite = favorite;
      this.created = created;
      this.from = from;
   }

   public JsonObject toJson() {
      JsonObject o = new JsonObject();
      o.addProperty("id", this.id);
      o.addProperty("name", this.name);
      o.addProperty("x", this.x);
      o.addProperty("y", this.y);
      o.addProperty("z", this.z);
      o.addProperty("dim", this.dim);
      o.addProperty("cat", this.category.name());
      o.addProperty("fav", this.favorite);
      o.addProperty("created", this.created);
      if (this.from != null) o.addProperty("from", this.from);
      if (this.home != null) o.addProperty("home", this.home);
      return o;
   }

   public static Waypoint fromJson(JsonObject o) {
      Waypoint w = fromJsonPlain(o);
      if (o.has("home")) w.home = o.get("home").getAsString();
      return w;
   }

   private static Waypoint fromJsonPlain(JsonObject o) {
      Category cat;
      try {
         cat = Category.valueOf(o.get("cat").getAsString());
      } catch (RuntimeException e) {
         cat = Category.OTHER;
      }
      return new Waypoint(o.get("id").getAsString(), o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
         o.has("dim") ? o.get("dim").getAsString() : "overworld", cat, o.has("fav") && o.get("fav").getAsBoolean(),
         o.has("created") ? o.get("created").getAsLong() : 0, o.has("from") ? o.get("from").getAsString() : null);
   }

   /** The text other players see when you share it (Donut Maps recognises it in chat). */
   public String shareText() {
      return "[DonutMaps] " + this.name + " @ " + this.x + " " + this.y + " " + this.z + " " + this.dim;
   }
}
