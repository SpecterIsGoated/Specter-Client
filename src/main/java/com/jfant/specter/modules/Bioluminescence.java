package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Motion;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.fx.FxParams;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

public class Bioluminescence extends Module {
   private static Bioluminescence instance;
   private static volatile boolean on;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGlow = this.settings.createGroup("Where It Glows");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final SettingGroup sgUnder = this.settings.createGroup("Under Water");
   private final SettingGroup sgWhen = this.settings.createGroup("When");
   private final Setting<Double> intensity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("intensity")
            .description("How bright the glow is overall.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .build()
      );
   private final Setting<Integer> reach = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("reach")
            .description("How far from you the water glows, in blocks. It fades out toward this distance.")
            .defaultValue(80)
            .range(16, 192)
            .sliderRange(16, 192)
            .build()
      );
   private final Setting<Double> waveSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("movement")
            .description("How fast the glow drifts, laps at the shore and sparks. 0 holds it still.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .build()
      );
   private final Setting<Double> shore = this.sgGlow
      .add(
         new DoubleSetting.Builder()
            .name("shoreline")
            .description("The bright edge where the water meets the land, in bands that run up the beach like small waves.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> patches = this.sgGlow
      .add(
         new DoubleSetting.Builder()
            .name("bloom")
            .description("Soft glowing patches drifting over open water.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> sparkle = this.sgGlow
      .add(
         new DoubleSetting.Builder()
            .name("sparks")
            .description("Pinpoints of light that flare and die on the surface. There are more of them in the rain.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> wake = this.sgGlow
      .add(
         new DoubleSetting.Builder()
            .name("wakes")
            .description("Rings of light where you, your boat and nearby creatures stir the water. 0 turns them off.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Bioluminescence.Palette> palette = this.sgColors
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("palette"))
                  .description("The colours of the glow: a deep one for the dim parts and a bright one for where it flares. Custom lets you pick both."))
               .defaultValue(Bioluminescence.Palette.Tide))
            .build()
      );
   private final Setting<SettingColor> deepColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("deep-color")
            .description("The colour of the dim, wide glow.")
            .defaultValue(new SettingColor(20, 120, 255))
            .visible(() -> this.palette.get() == Bioluminescence.Palette.Custom)
            .build()
      );
   private final Setting<SettingColor> brightColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("bright-color")
            .description("The colour it shifts to where it is brightest.")
            .defaultValue(new SettingColor(60, 255, 230))
            .visible(() -> this.palette.get() == Bioluminescence.Palette.Custom)
            .build()
      );
   private final Setting<Double> motes = this.sgUnder
      .add(
         new DoubleSetting.Builder()
            .name("motes")
            .description("Specks of light drifting in the water around you when you are under it.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> floorGlow = this.sgUnder
      .add(
         new DoubleSetting.Builder()
            .name("sea-bed-glow")
            .description("A slow net of light moving over the sea bed near you.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Boolean> nightOnly = this.sgWhen
      .add(
         new BoolSetting.Builder()
            .name("night-only")
            .description("Glow only after dark, rising through dusk and fading at dawn. Off, it glows all day (it shows best in shade).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> dayLevel = this.sgWhen
      .add(
         new DoubleSetting.Builder()
            .name("day-level")
            .description("How much of the glow is left in daylight.")
            .defaultValue(0.0)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.nightOnly::get)
            .build()
      );
   private final Setting<Boolean> caves = this.sgWhen
      .add(
         new BoolSetting.Builder()
            .name("glow-in-caves")
            .description("Underground water, out of the sun, glows whatever the time of day.")
            .defaultValue(true)
            .build()
      );
   private final Tween presence = Tween.progress(Spec.WORLD_FADE, false);
   private final Spring sunless = new Spring(2.5, Channel.WORLD, 0.0);
   private float sunlessTarget;
   private final double[] wakeX = new double[12];
   private final double[] wakeZ = new double[12];
   private final double[] wakeBorn = new double[12];
   private final float[] wakeStrength = new float[12];
   private int wakeNext;
   private int tick;
   private static final double WAKE_SECONDS = 4.0;

   public Bioluminescence() {
      super(
         SpecterClientAddon.VISUALS,
         "bioluminescence",
         "Water glows blue-green at night: along the shore, in drifting patches, in sparks, and in your wake. Only you see it."
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      on = true;
      this.presence.to(1.0, Spec.WORLD_FADE);
   }

   @Override
   public void onDeactivate() {
      this.presence.to(0.0, Spec.WORLD_FADE);
   }

   public static boolean on() {
      return on;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.level != null && this.mc.player != null) {
         this.tick++;
         if (this.tick % 10 == 0) {
            BlockPos at = BlockPos.containing(this.mc.gameRenderer.mainCamera().position());
            int sky = this.mc.level.getBrightness(LightLayer.SKY, at);
            this.sunlessTarget = this.caves.get() ? Math.clamp((6 - sky) / 6.0F, 0.0F, 1.0F) : 0.0F;
         }

         if (!(this.wake.get() <= 0.0)) {
            Entity self = (Entity)(this.mc.player.getVehicle() != null ? this.mc.player.getVehicle() : this.mc.player);
            if (this.tick % 5 == 0) {
               this.stir(self, 1.0F);
            }

            if (this.tick % 3 == 0) {
               int index = 0;
               int pick = this.tick / 3 % 24;

               for (Entity entity : this.mc.level.entitiesForRendering()) {
                  if (entity != self && entity != this.mc.player && !(entity.distanceToSqr(this.mc.player) > 1600.0) && entity.isInWater() && index++ == pick) {
                     this.stir(entity, 0.7F);
                     break;
                  }
               }
            }
         }
      }
   }

   private void stir(Entity entity, float scale) {
      if (entity.isInWater()) {
         Vec3 v = entity.getDeltaMovement();
         double speed = Math.sqrt(v.x * v.x + v.z * v.z) + Math.abs(v.y) * 0.5;
         if (!(speed < 0.035)) {
            int i = this.wakeNext++ % 12;
            this.wakeX[i] = entity.getX();
            this.wakeZ[i] = entity.getZ();
            this.wakeBorn[i] = Motion.now();
            this.wakeStrength[i] = (float)Math.min(1.4, 0.35 + speed * 3.2) * scale;
         }
      }
   }

   public static void fill(FxParams p) {
      if (instance != null && on) {
         instance.fillParams(p);
      }
   }

   private void fillParams(FxParams p) {
      float shown = this.presence.getF();
      if (shown <= 0.001F && !this.isActive()) {
         on = false;
      } else if (p.overworld) {
         float dark = this.nightOnly.get() ? Math.max(p.night, this.dayLevel.get().floatValue()) : 1.0F;
         dark = Math.max(dark, (float)this.sunless.follow(this.sunlessTarget));
         float strength = this.intensity.get().floatValue() * shown * dark;
         if (!(strength <= 0.001F)) {
            p.bio = true;
            p.bioStrength = strength;
            p.bioShore = this.shore.get().floatValue();
            p.bioSparkle = this.sparkle.get().floatValue();
            p.bioPatches = this.patches.get().floatValue();
            p.bioWake = this.wake.get().floatValue();
            p.bioMotes = this.motes.get().floatValue();
            p.bioFloor = this.floorGlow.get().floatValue();
            p.bioReach = this.reach.get().intValue();
            p.bioWaveSpeed = this.waveSpeed.get().floatValue();
            Bioluminescence.Palette chosen = this.palette.get();
            if (chosen == Bioluminescence.Palette.Custom) {
               linear(this.deepColor.get().r, this.deepColor.get().g, this.deepColor.get().b, p.bioDeep);
               linear(this.brightColor.get().r, this.brightColor.get().g, this.brightColor.get().b, p.bioBright);
            } else {
               linear(chosen.deep >> 16 & 0xFF, chosen.deep >> 8 & 0xFF, chosen.deep & 0xFF, p.bioDeep);
               linear(chosen.bright >> 16 & 0xFF, chosen.bright >> 8 & 0xFF, chosen.bright & 0xFF, p.bioBright);
            }

            double now = Motion.now();

            for (int i = 0; i < 12; i++) {
               double age = now - this.wakeBorn[i];
               if (!(this.wakeStrength[i] <= 0.0F) && !(age > 4.0) && !(age < 0.0)) {
                  p.wakes[i * 4] = (float)(this.wakeX[i] - p.worldX);
                  p.wakes[i * 4 + 1] = (float)(this.wakeZ[i] - p.worldZ);
                  p.wakes[i * 4 + 2] = (float)age;
                  p.wakes[i * 4 + 3] = this.wakeStrength[i];
               }
            }
         }
      }
   }

   private static void linear(int r, int g, int b, float[] out) {
      out[0] = (float)Math.pow(r / 255.0, 2.2);
      out[1] = (float)Math.pow(g / 255.0, 2.2);
      out[2] = (float)Math.pow(b / 255.0, 2.2);
   }

   public static enum Palette {
      Tide("Cyan Tide", 1341695, 3997670),
      Turquoise("Turquoise", 1024952, 8257504),
      Deep("Deep Blue", 1325030, 5220607),
      Aurora("Aurora Green", 1030258, 11861900),
      Violet("Specter Violet", 6959846, 16747750),
      Custom("Custom", 0, 0);

      private final String title;
      final int deep;
      final int bright;

      private Palette(String title, int deep, int bright) {
         this.title = title;
         this.deep = deep;
         this.bright = bright;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
