package com.jfant.specter.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.renderer.ShapeMode;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.pvp.PvpCard;
import com.jfant.specter.render.CardSpot;
import com.jfant.specter.render.CardText;
import com.jfant.specter.render.Corner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

public class AnticheatAlerts extends Module {
   private static final int WIDTH = 168;
   private static final int MAX_ROWS = 5;
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgCard = this.settings.createGroup("Card");
   private final SettingGroup sgBeams = this.settings.createGroup("Beams");
   private final Setting<String> feedUrl = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("feed-url")
            .description("Address of the anticheat feed.")
            .defaultValue("https://anticheat.imjules.be/api.json")
            .build()
      );
   private final Setting<Integer> interval = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("check-every")
            .description("Seconds between two looks at the feed.")
            .defaultValue(10)
            .range(3, 120)
            .sliderRange(3, 60)
            .build()
      );
   private final Setting<AnticheatAlerts.RegionMap> regionMap = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("region-map"))
                  .description("Which DonutSMP region map the G# numbers follow."))
               .defaultValue(AnticheatAlerts.RegionMap.New))
            .build()
      );
   private final Setting<Boolean> testAlerts = this.sgGeneral
      .add(new BoolSetting.Builder().name("test-alerts").description("Also show the feed's test entries.").defaultValue(false).build());
   private final Setting<Boolean> sound = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("sound")
            .description("Play a sound when the anticheat goes off somewhere. Only you hear it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> chatMessage = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("chat-message")
            .description("Say it in chat when the anticheat goes off or comes back on. Only you see it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> card = this.sgCard
      .add(
         new BoolSetting.Builder()
            .name("status-card")
            .description("A card with whether the anticheat is on, and every place it is off. Drag it in the HUD editor.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> hideWhenOn = this.sgCard
      .add(
         new BoolSetting.Builder()
            .name("hide-when-on")
            .description("Hide the card while the anticheat is on everywhere.")
            .defaultValue(false)
            .visible(this.card::get)
            .build()
      );
   private final CardSpot spot = new CardSpot(this, this.sgCard, Corner.TopRight, -4, 120);
   private final Setting<Double> scale = this.sgCard
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("Size of the card. 1 is the game's own GUI size.")
            .defaultValue(0.65)
            .min(0.25)
            .sliderRange(0.25, 2.0)
            .visible(this.card::get)
            .build()
      );
   private final Setting<Boolean> beams = this.sgBeams
      .add(
         new BoolSetting.Builder()
            .name("beams")
            .description("A beam into the sky over every place the anticheat is off (overworld only).")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> beamColor = this.sgBeams
      .add(
         new ColorSetting.Builder()
            .name("beam-color")
            .description("Colour of the beams.")
            .defaultValue(new SettingColor(110, 255, 160, 70))
            .visible(this.beams::get)
            .build()
      );
   private volatile List<AnticheatAlerts.Spot> spots = List.of();
   private volatile String error;
   private volatile long fetchedAt;
   private final AtomicBoolean fetching = new AtomicBoolean();
   private long nextFetch;
   private final Set<String> known = new HashSet<>();
   private boolean loaded;
   private List<AnticheatAlerts.Spot> applied = List.of();

   public AnticheatAlerts() {
      super(
         SpecterClientAddon.HUD,
         "anticheat-alerts",
         "Tells you when and where DonutSMP's anticheat goes off, from the public anticheat feed: alerts, a status card and beams."
      );
   }

   @Override
   public void onActivate() {
      this.nextFetch = 0L;
      this.loaded = false;
      this.known.clear();
      this.applied = List.of();
   }

   @Override
   public void onDeactivate() {
      this.spots = List.of();
      this.error = null;
      this.known.clear();
   }

   @Override
   public String getInfoString() {
      int off = this.visible().size();
      return off == 0 ? null : Integer.toString(off);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      long now = System.currentTimeMillis();
      if (now >= this.nextFetch && this.fetching.compareAndSet(false, true)) {
         this.nextFetch = now + this.interval.get().intValue() * 1000L;
         this.fetch(this.feedUrl.get().trim());
      }

      List<AnticheatAlerts.Spot> latest = this.spots;
      if (latest != this.applied) {
         this.applied = latest;
         this.announce(this.visible());
      }
   }

   private void fetch(String url) {
      HttpRequest request;
      try {
         request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8L)).header("User-Agent", "Mozilla/5.0").GET().build();
      } catch (IllegalArgumentException var4) {
         this.error = "Bad feed address";
         this.fetching.set(false);
         return;
      }

      HTTP.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, failure) -> {
         try {
            if (failure != null) {
               this.error = "Feed unreachable";
            } else if (response.statusCode() != 200) {
               this.error = "Feed answered " + response.statusCode();
            } else {
               this.spots = parse(response.body());
               this.error = null;
               this.fetchedAt = System.currentTimeMillis();
            }
         } catch (RuntimeException var7) {
            this.error = "Feed unreadable";
         } finally {
            this.fetching.set(false);
         }
      });
   }

   private static List<AnticheatAlerts.Spot> parse(String body) {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      List<AnticheatAlerts.Spot> out = new ArrayList<>();
      if (root.has("alerts") && root.get("alerts").isJsonArray()) {
         for (JsonElement element : root.getAsJsonArray("alerts")) {
            if (element.isJsonObject()) {
               JsonObject o = element.getAsJsonObject();
               int number = integer(o, "goliath", -1);
               int newNumber = integer(o, "newGoliath", number);
               int oldNumber = integer(o, "oldGoliath", number);
               String region = text(o, "region", "Unknown");
               String coords = text(o, "coords", "");
               double x = 0.0;
               double y = 0.0;
               double z = 0.0;
               if (o.has("position") && o.get("position").isJsonObject()) {
                  JsonObject p = o.getAsJsonObject("position");
                  x = number(p, "x");
                  y = number(p, "y");
                  z = number(p, "z");
               }

               boolean test = o.has("test") && o.get("test").getAsBoolean();
               out.add(new AnticheatAlerts.Spot(region + "|" + newNumber + "|" + coords, region, newNumber, oldNumber, coords, x, y, z, test));
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private static int integer(JsonObject o, String key, int fallback) {
      return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
   }

   private static double number(JsonObject o, String key) {
      return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : 0.0;
   }

   private static String text(JsonObject o, String key, String fallback) {
      return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
   }

   private List<AnticheatAlerts.Spot> visible() {
      List<AnticheatAlerts.Spot> out = new ArrayList<>();

      for (AnticheatAlerts.Spot s : this.spots) {
         if (!s.test() || this.testAlerts.get()) {
            out.add(s);
         }
      }

      return out;
   }

   private void announce(List<AnticheatAlerts.Spot> now) {
      Set<String> keys = new HashSet<>();
      List<AnticheatAlerts.Spot> fresh = new ArrayList<>();

      for (AnticheatAlerts.Spot s : now) {
         keys.add(s.key());
         if (!this.known.contains(s.key())) {
            fresh.add(s);
         }
      }

      boolean wasOff = !this.known.isEmpty();
      boolean first = !this.loaded;
      this.loaded = true;
      this.known.clear();
      this.known.addAll(keys);
      if (!fresh.isEmpty()) {
         if (first && fresh.size() > 1) {
            this.say("The anticheat is OFF at " + fresh.size() + " places right now - see the card.");
         } else {
            for (AnticheatAlerts.Spot sx : fresh) {
               this.say("Anticheat OFF" + (sx.test() ? " (test)" : "") + ": " + this.describe(sx));
            }
         }

         if (this.sound.get()) {
            this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F, 0.8F));
         }
      } else if (wasOff && now.isEmpty()) {
         this.say("The anticheat is back ON everywhere.");
      }
   }

   private void say(String message) {
      if (this.chatMessage.get()) {
         this.info(message, new Object[0]);
      }
   }

   private String place(AnticheatAlerts.Spot s) {
      int number = this.regionMap.get() == AnticheatAlerts.RegionMap.New ? s.newNumber() : s.oldNumber();
      return s.region() + (number >= 0 ? " G#" + number : "");
   }

   private String describe(AnticheatAlerts.Spot s) {
      StringBuilder b = new StringBuilder(this.place(s));
      if (!s.coords().isEmpty()) {
         b.append(" - ").append(s.coords());
      }

      if (s.placed() && this.mc.player != null) {
         double dx = this.mc.player.getX() - s.x();
         double dz = this.mc.player.getZ() - s.z();
         b.append(" - ").append(Math.round(Math.sqrt(dx * dx + dz * dz))).append(" blocks away");
      }

      return b.toString();
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.card.get() && PvpCard.visible()) {
         List<AnticheatAlerts.Spot> off = this.visible();
         if (!off.isEmpty() || !this.hideWhenOn.get() || this.error != null) {
            this.draw(event.drawContext, off);
         }
      }
   }

   private void draw(GuiGraphics ctx, List<AnticheatAlerts.Spot> off) {
      int inner = 158;
      int rows = Math.min(off.size(), 5);
      int height = 12 + (rows == 0 ? 0 : rows * 12) + (off.size() > 5 ? 12 : 0) + 12;
      PvpCard.begin(ctx, this.spot, this.scale.get(), 168, height - 3);
      CardText.draw(ctx, "Anticheat", 0.0F, 0.0F, -1065473, true);
      String state = this.error != null && this.fetchedAt == 0L ? "?" : (off.isEmpty() ? "ON" : "OFF x" + off.size());
      int fill = off.isEmpty() ? -12965291 : -9705867;
      PvpCard.badge(ctx, state, inner, 0, fill, off.isEmpty() ? -1 : -15069440);
      int y = 12;

      for (int i = 0; i < rows; i++) {
         AnticheatAlerts.Spot s = off.get(i);
         String left = this.place(s);
         CardText.draw(ctx, left, 0.0F, (float)y, -1, true);
         String right = s.coords().isEmpty() ? "" : s.coords();
         if (s.placed() && this.mc.player != null) {
            double dx = this.mc.player.getX() - s.x();
            double dz = this.mc.player.getZ() - s.z();
            right = (right.isEmpty() ? "" : right + "  ") + Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
         }

         CardText.draw(ctx, right, (float)(inner - CardText.width(right)), (float)y, -4149795, true);
         y += 12;
      }

      if (off.size() > 5) {
         CardText.draw(ctx, "+" + (off.size() - 5) + " more", 0.0F, (float)y, -4149795, true);
         y += 12;
      }

      String foot = this.error != null ? this.error : (this.fetchedAt == 0L ? "Checking the feed..." : "Checked " + ago(this.fetchedAt));
      CardText.draw(ctx, foot, 0.0F, (float)y, this.error != null ? -22016 : -4149795, true);
      PvpCard.end(ctx);
   }

   private static String ago(long at) {
      long s = Math.max(0L, (System.currentTimeMillis() - at) / 1000L);
      return s < 60L ? s + "s ago" : s / 60L + "m ago";
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.beams.get() && this.mc.level != null && this.mc.level.dimension() == Level.OVERWORLD) {
         SettingColor color = this.beamColor.get();
         SettingColor line = new SettingColor(color.r, color.g, color.b, Math.min(255, color.a * 3));

         for (AnticheatAlerts.Spot s : this.visible()) {
            if (s.placed()) {
               double x = s.x();
               double z = s.z();
               event.renderer.box(x - 0.6, this.mc.level.getMinY(), z - 0.6, x + 0.6, 320.0, z + 0.6, color, line, ShapeMode.Both, 0);
            }
         }
      }
   }

   public static enum RegionMap {
      New("New"),
      Old("Old");

      private final String title;

      private RegionMap(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private record Spot(String key, String region, int newNumber, int oldNumber, String coords, double x, double y, double z, boolean test) {
      boolean placed() {
         return this.x != 0.0 || this.z != 0.0;
      }
   }
}
