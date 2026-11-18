package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.render.Render3DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.ProvidedStringSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringListSetting;
import com.jfant.specter.core.systems.modules.Module;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

public class TimeSet extends Module {
   private static final int DAY = 24000;
   private static final TimeSet.Time[] CYCLE = new TimeSet.Time[]{
      TimeSet.Time.Sunrise,
      TimeSet.Time.Morning,
      TimeSet.Time.Noon,
      TimeSet.Time.Afternoon,
      TimeSet.Time.GoldenHour,
      TimeSet.Time.Sunset,
      TimeSet.Time.Dusk,
      TimeSet.Time.Midnight
   };
   private static final String NONE_SAVED = "None saved";
   private static TimeSet instance;
   private static volatile boolean controlling;
   private static volatile boolean transitioning;
   private static volatile long shownTod;
   private final SettingGroup sgTime = this.settings.getDefaultGroup();
   private final SettingGroup sgCycle = this.settings.createGroup("Cycle");
   private final SettingGroup sgSmooth = this.settings.createGroup("Transitions");
   private final Setting<TimeSet.Time> time = this.sgTime
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("time"))
                  .description("The time of day you see. Server shows the server's real time."))
               .defaultValue(TimeSet.Time.Noon))
            .build()
      );
   private final Setting<Integer> customTime = this.sgTime
      .add(
         new IntSetting.Builder()
            .name("custom-time")
            .description("Your own time of day, in ticks: 0 sunrise, 6000 noon, 12000 sunset, 18000 midnight.")
            .defaultValue(6000)
            .range(0, 23999)
            .sliderRange(0, 23999)
            .visible(() -> this.time.get() == TimeSet.Time.Custom && !this.autoCycle.get())
            .build()
      );
   private final Setting<String> savedPreset = this.sgTime
      .add(
         new ProvidedStringSetting.Builder()
            .name("saved-preset")
            .description("One of your saved times. Save the current one with .timeset save <name>.")
            .defaultValue("")
            .supplier(() -> {
               List<String> names = new ArrayList<>(this.savedMap().keySet());
               if (names.isEmpty()) {
                  names.add("None saved");
               }

               return names.toArray(new String[0]);
            })
            .visible(() -> this.time.get() == TimeSet.Time.Saved && !this.autoCycle.get())
            .build()
      );
   private final Setting<List<String>> savedTimes = this.sgTime
      .add(
         new StringListSetting.Builder()
            .name("saved-times")
            .description("Your saved times, written Name=ticks. Edit them here or use .timeset save / delete.")
            .build()
      );
   private final Setting<Boolean> restoreDefault = this.sgTime
      .add(
         new BoolSetting.Builder()
            .name("restore-default")
            .description("Click to show the server's real time again. Switches itself back off.")
            .defaultValue(false)
            .onChanged(v -> {
               if (v) {
                  this.time.set(TimeSet.Time.Server);
                  this.autoCycle.set(false);
                  this.override.set(true);
                  this.mc.execute(() -> this.restoreDefault.set(false));
               }
            })
            .build()
      );
   private final Setting<Boolean> override = this.sgTime
      .add(
         new BoolSetting.Builder()
            .name("override")
            .description("Enable or disable the time override. Off shows the server's time (smoothly) without turning the module off.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> freeze = this.sgCycle
      .add(
         new BoolSetting.Builder()
            .name("freeze")
            .description("The time you see stands still. Off lets it run forward from the chosen time.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> cycleSpeed = this.sgCycle
      .add(
         new DoubleSetting.Builder()
            .name("cycle-speed")
            .description("How fast your day and night run. 1 is vanilla speed (20 minutes a day).")
            .defaultValue(1.0)
            .range(0.1, 50.0)
            .sliderRange(0.1, 20.0)
            .visible(() -> !this.freeze.get())
            .build()
      );
   private final Setting<Boolean> autoCycle = this.sgCycle
      .add(
         new BoolSetting.Builder()
            .name("auto-cycle")
            .description("Steps through sunrise, morning, noon, afternoon, golden hour, sunset, dusk and midnight by itself. Takes over from the time setting.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> cycleInterval = this.sgCycle
      .add(
         new IntSetting.Builder()
            .name("cycle-interval")
            .description("Seconds between steps of the automatic cycle.")
            .defaultValue(15)
            .range(3, 120)
            .sliderRange(3, 120)
            .visible(this.autoCycle::get)
            .build()
      );
   private final Setting<Boolean> smooth = this.sgSmooth
      .add(
         new BoolSetting.Builder()
            .name("smooth")
            .description("Ease to a new time (and back to the server's when you turn this off) instead of jumping.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> transitionTime = this.sgSmooth
      .add(
         new DoubleSetting.Builder()
            .name("transition-time")
            .description("Seconds a change takes. It always runs forward through the day, so noon to midnight passes through sunset.")
            .defaultValue(2.5)
            .range(0.2, 10.0)
            .sliderRange(0.2, 10.0)
            .visible(this.smooth::get)
            .build()
      );
   private volatile long realTod;
   private volatile boolean realTicks = true;
   private ClientLevel world;
   private double shownPhase;
   private double from;
   private double transT;
   private double transDur;
   private boolean back;
   private double runPhase;
   private long lastKey = -2L;
   private long lastNanos;
   private int cycleIndex;
   private double cycleTimer;
   private boolean wasAuto;
   private boolean releasing;
   private final TimeSet.Releaser releaser = new TimeSet.Releaser();

   public TimeSet() {
      super(
         SpecterClientAddon.VISUALS,
         "time-set",
         "Pick the time of day you see: presets, a slider, smooth transitions, your own cycle speed. Only you see it; the server's time is untouched."
      );
      instance = this;
   }

   public static boolean overriding() {
      return controlling;
   }

   public static long shownTimeOfDay() {
      if (controlling) {
         return shownTod;
      } else {
         ClientLevel w = Minecraft.getInstance().level;
         return w == null ? 6000L : w.getDayTime();
      }
   }

   public static boolean transitioning() {
      return transitioning;
   }

   @Override
   public void onActivate() {
      this.lastNanos = System.nanoTime();
      this.lastKey = -2L;
      this.wasAuto = false;
      ClientLevel w = this.mc.level;
      if (this.releasing) {
         this.releasing = false;
         SpecterCore.EVENT_BUS.unsubscribe(this.releaser);
         if (w != this.world) {
            this.clearState();
         }
      } else if (!controlling || w != this.world) {
         this.clearState();
      }

      if (!controlling || w != this.world) {
         this.world = w;
         if (w != null) {
            this.realTod = w.getDayTime();
            this.shownPhase = mod(this.realTod);
         }
      }
   }

   @Override
   public void onDeactivate() {
      ClientLevel w = this.mc.level;
      if (this.smooth.get() && controlling && w != null && w == this.world) {
         this.releasing = true;
         this.lastKey = -2L;
         this.lastNanos = System.nanoTime();
         SpecterCore.EVENT_BUS.subscribe(this.releaser);
      } else {
         if (controlling && w != null && w == this.world) {
            this.restore(w);
         }

         this.clearState();
      }
   }

   private void clearState() {
      controlling = false;
      transitioning = false;
   }

   private void finishRelease() {
      this.releasing = false;
      SpecterCore.EVENT_BUS.unsubscribe(this.releaser);
      this.clearState();
   }

   @EventHandler
   private void onFrame(Render3DEvent event) {
      this.update();
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      this.handlePacket(event);
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.handleTick();
   }

   private void handlePacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundSetTimePacket packet) {
         if (controlling) {
            event.cancel();
         }

         boolean cancelled = event.isCancelled();
         this.mc.execute(() -> {
            ClientLevel w = this.mc.level;
            if (w != null && w != this.world) {
               this.world = w;
            }

            this.realTod = packet.dayTime();
            this.realTicks = packet.tickDayTime();
            if (cancelled && w != null) {
               w.setTimeFromServer(packet.gameTime(), this.displayed(), false);
            }
         });
      }
   }

   private void handleTick() {
      ClientLevel w = this.mc.level;
      if (w != null && w == this.world) {
         if (controlling) {
            if (this.realTicks) {
               this.realTod++;
            }
         } else {
            this.realTod = w.getDayTime();
         }
      }
   }

   private void update() {
      long now = System.nanoTime();
      double dt = Math.min(0.25, Math.max(0.0, (now - this.lastNanos) / 1.0E9));
      this.lastNanos = now;
      ClientLevel w = this.mc.level;
      if (w != null) {
         if (w != this.world) {
            this.world = w;
            this.realTod = w.getDayTime();
            if (this.releasing) {
               this.finishRelease();
               return;
            }
         }

         boolean auto = this.autoCycle.get() && !this.releasing && this.override.get();
         boolean server = this.releasing || !this.override.get() || !auto && this.time.get() == TimeSet.Time.Server;
         if (auto) {
            if (!this.wasAuto) {
               this.cycleIndex = this.nextPresetAhead();
               this.cycleTimer = 0.0;
            } else {
               this.cycleTimer += dt;
               if (this.cycleTimer >= this.cycleInterval.get().intValue()) {
                  this.cycleTimer = 0.0;
                  this.cycleIndex = (this.cycleIndex + 1) % CYCLE.length;
               }
            }
         }

         this.wasAuto = auto;
         int chosen = server ? 0 : (auto ? CYCLE[this.cycleIndex].ticks : this.chosenTicks());
         boolean running = !server && !this.freeze.get();
         long key = server ? -1L : chosen * 2L + (running ? 1 : 0);
         if (key != this.lastKey) {
            this.lastKey = key;
            this.runPhase = chosen;
            this.retarget(server, running, chosen);
         } else if (running) {
            this.runPhase = mod(this.runPhase + dt * 20.0 * this.cycleSpeed.get());
         }

         double target = this.target(server, running, chosen);
         if (transitioning) {
            if (!this.smooth.get()) {
               transitioning = false;
            } else {
               this.transT += dt;
               double p = this.transT / this.transDur;
               if (p >= 1.0) {
                  transitioning = false;
                  this.shownPhase = target;
               } else {
                  double e = p * p * (3.0 - 2.0 * p);
                  double d = mod(target - this.from);
                  if (this.back && d > 0.0) {
                     d -= 24000.0;
                  }

                  this.shownPhase = mod(this.from + e * d);
               }
            }
         }

         if (!transitioning) {
            this.shownPhase = target;
         }

         if (transitioning || !server) {
            controlling = true;
            shownTod = this.displayed();
            w.setTimeFromServer(w.getGameTime(), shownTod, false);
         } else if (controlling) {
            this.restore(w);
            if (this.releasing) {
               this.finishRelease();
            } else {
               this.clearState();
            }
         }
      }
   }

   private void retarget(boolean server, boolean running, int chosen) {
      if (!this.smooth.get()) {
         transitioning = false;
      } else {
         double fwd = mod(this.target(server, running, chosen) - this.shownPhase);
         if (!(fwd < 0.5) && !(fwd > 23999.5)) {
            this.from = this.shownPhase;
            this.back = fwd > 18000.0 && 24000.0 - fwd < 4000.0;
            this.transT = 0.0;
            this.transDur = Math.max(0.2, this.transitionTime.get());
            transitioning = true;
         } else {
            transitioning = false;
         }
      }
   }

   private double target(boolean server, boolean running, int chosen) {
      if (server) {
         return mod(this.realTod);
      } else {
         return running ? this.runPhase : chosen;
      }
   }

   private long displayed() {
      return Math.floorDiv(this.realTod, 24000L) * 24000L + (long)this.shownPhase;
   }

   private void restore(ClientLevel w) {
      w.setTimeFromServer(w.getGameTime(), this.realTod, this.realTicks);
   }

   private static double mod(double v) {
      double m = v % 24000.0;
      return m < 0.0 ? m + 24000.0 : m;
   }

   private static double mod(long v) {
      return Math.floorMod(v, 24000L);
   }

   private int nextPresetAhead() {
      int best = 0;
      double bestDist = 1.7976931348623157E308;

      for (int i = 0; i < CYCLE.length; i++) {
         double d = mod(CYCLE[i].ticks - this.shownPhase);
         if (d < 0.5) {
            d += 24000.0;
         }

         if (d < bestDist) {
            bestDist = d;
            best = i;
         }
      }

      return best;
   }

   private int chosenTicks() {
      return switch ((TimeSet.Time)this.time.get()) {
         case Custom -> this.customTime.get();
         case Saved -> {
            Map<String, Integer> saved = this.savedMap();
            Integer ticks = saved.get(this.savedPreset.get());
            if (ticks == null && !saved.isEmpty()) {
               ticks = saved.values().iterator().next();
            }

            yield ticks == null ? TimeSet.Time.Noon.ticks : ticks;
         }
         case Server -> TimeSet.Time.Noon.ticks;
         default -> this.time.get().ticks;
      };
   }

   public Map<String, Integer> savedMap() {
      Map<String, Integer> map = new LinkedHashMap<>();

      for (String entry : this.savedTimes.get()) {
         if (entry != null) {
            int eq = entry.lastIndexOf(61);
            if (eq > 0) {
               String name = entry.substring(0, eq).trim();
               if (!name.isEmpty()) {
                  try {
                     map.put(name, (int)Math.floorMod(Long.parseLong(entry.substring(eq + 1).trim()), 24000L));
                  } catch (NumberFormatException var7) {
                  }
               }
            }
         }
      }

      return map;
   }

   private String findSaved(String name) {
      for (String key : this.savedMap().keySet()) {
         if (key.equalsIgnoreCase(name.trim())) {
            return key;
         }
      }

      return null;
   }

   private void writeSaved(Map<String, Integer> map) {
      List<String> list = new ArrayList<>();

      for (Entry<String, Integer> e : map.entrySet()) {
         list.add(e.getKey() + "=" + e.getValue());
      }

      this.savedTimes.set(list);
   }

   public int save(String name) {
      name = name.replace('=', '-').trim();
      int ticks = (int)mod(shownTimeOfDay());
      Map<String, Integer> map = this.savedMap();
      String existing = this.findSaved(name);
      if (existing != null) {
         map.remove(existing);
      }

      map.put(name, ticks);
      this.writeSaved(map);
      return ticks;
   }

   public boolean load(String name) {
      String key = this.findSaved(name);
      if (key == null) {
         return false;
      } else {
         this.savedPreset.set(key);
         this.choose(TimeSet.Time.Saved);
         return true;
      }
   }

   public boolean delete(String name) {
      String key = this.findSaved(name);
      if (key == null) {
         return false;
      } else {
         Map<String, Integer> map = this.savedMap();
         map.remove(key);
         this.writeSaved(map);
         return true;
      }
   }

   public boolean setFromText(String text) {
      String t = text.trim();

      try {
         this.customTime.set((int)Math.floorMod(Long.parseLong(t), 24000L));
         this.choose(TimeSet.Time.Custom);
         return true;
      } catch (NumberFormatException var8) {
         String norm = t.toLowerCase().replaceAll("[\\s_-]", "");

         for (TimeSet.Time preset : TimeSet.Time.values()) {
            if (preset != TimeSet.Time.Custom && preset != TimeSet.Time.Saved && preset.title.toLowerCase().replace(" ", "").equals(norm)) {
               this.choose(preset);
               return true;
            }
         }

         return false;
      }
   }

   public void reset() {
      this.choose(TimeSet.Time.Server);
   }

   private void choose(TimeSet.Time t) {
      this.time.set(t);
      this.autoCycle.set(false);
      this.override.set(true);
   }

   public static String clock(int ticks) {
      int minutes = (int)(ticks % 24000 * 60L / 1000L);
      int hour = (6 + minutes / 60) % 24;
      return String.format("%02d:%02d", hour, minutes % 60);
   }

   public static class Releaser {
      @EventHandler
      public void onFrame(Render3DEvent event) {
         if (TimeSet.instance != null) {
            TimeSet.instance.update();
         }
      }

      @EventHandler
      public void onPacket(PacketEvent.Receive event) {
         if (TimeSet.instance != null) {
            TimeSet.instance.handlePacket(event);
         }
      }

      @EventHandler
      public void onTick(TickEvent.Post event) {
         if (TimeSet.instance != null) {
            TimeSet.instance.handleTick();
         }
      }
   }

   public static enum Time {
      Sunrise("Sunrise", 23400),
      Morning("Morning", 1000),
      Noon("Noon", 6000),
      Afternoon("Afternoon", 9000),
      GoldenHour("Golden hour", 12150),
      Sunset("Sunset", 12650),
      Dusk("Dusk", 13150),
      Midnight("Midnight", 18000),
      Custom("Custom", -1),
      Saved("Saved", -1),
      Server("Server", -1);

      private final String title;
      final int ticks;

      private Time(String title, int ticks) {
         this.title = title;
         this.ticks = ticks;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
