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
import com.jfant.specter.sky.SkyModel;
import com.jfant.specter.sky.SkyRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

public class MarineLayer extends Module {
   private static MarineLayer instance;
   private static volatile boolean on;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgShape = this.settings.createGroup("Shape");
   private final SettingGroup sgLook = this.settings.createGroup("Look");
   private final SettingGroup sgWhere = this.settings.createGroup("Where");
   private final Setting<MarineLayer.Preset> preset = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("preset"))
                  .description(
                     "How heavy the fog is. Light Haze softens the distance; Morning Mist lies low over the water; Coastal Fog closes the view in; Dense Bank is a wall of it. Custom shows every dial."
                  ))
               .defaultValue(MarineLayer.Preset.Coastal))
            .build()
      );
   private final Setting<Double> amount = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("amount")
            .description("Turns the whole layer up or down without changing its shape.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> density = this.sgShape
      .add(
         new DoubleSetting.Builder()
            .name("density")
            .description("How thick the fog is at its base: how quickly things fade as they get further away.")
            .defaultValue(0.028)
            .range(0.0, 0.15)
            .sliderRange(0.0, 0.1)
            .decimalPlaces(3)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Double> opacity = this.sgShape
      .add(
         new DoubleSetting.Builder()
            .name("opacity")
            .description("The most the fog may hide. Below 1 the far land always shows through a little.")
            .defaultValue(0.86)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Integer> height = this.sgShape
      .add(
         new IntSetting.Builder()
            .name("height")
            .description("How tall the layer is, in blocks above its base. Low hugs the water; high swallows hills.")
            .defaultValue(20)
            .range(2, 120)
            .sliderRange(2, 80)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Integer> distance = this.sgShape
      .add(
         new IntSetting.Builder()
            .name("distance")
            .description("How far from you the fog begins. Nearer than this the air is clear.")
            .defaultValue(20)
            .range(0, 200)
            .sliderRange(0, 120)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Double> patchiness = this.sgShape
      .add(
         new DoubleSetting.Builder()
            .name("patchiness")
            .description("0 is an even blanket; higher breaks it into banks with clear lanes between them.")
            .defaultValue(0.5)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Integer> patchSize = this.sgShape
      .add(
         new IntSetting.Builder()
            .name("patch-size")
            .description("How big the banks are, in blocks.")
            .defaultValue(170)
            .range(40, 600)
            .sliderRange(40, 400)
            .visible(() -> this.preset.get() == MarineLayer.Preset.Custom)
            .build()
      );
   private final Setting<Integer> base = this.sgShape
      .add(
         new IntSetting.Builder()
            .name("base-level")
            .description("The height the fog lies at. 63 is sea level.")
            .defaultValue(63)
            .range(-64, 320)
            .sliderRange(40, 120)
            .build()
      );
   private final Setting<Double> drift = this.sgLook
      .add(
         new DoubleSetting.Builder()
            .name("drift")
            .description("How fast the banks move on the wind. 0 holds them still.")
            .defaultValue(1.0)
            .range(0.0, 5.0)
            .sliderRange(0.0, 3.0)
            .build()
      );
   private final Setting<MarineLayer.Tone> tone = this.sgLook
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("color"))
                  .description(
                     "Natural takes its colour from the hour: pale by day, warm at sunrise and sunset, blue-grey at night (and from Atmosphere's sky when that is on). Custom uses your own."
                  ))
               .defaultValue(MarineLayer.Tone.Natural))
            .build()
      );
   private final Setting<SettingColor> customColor = this.sgLook
      .add(
         new ColorSetting.Builder()
            .name("fog-color")
            .description("The colour of the fog.")
            .defaultValue(new SettingColor(214, 222, 232))
            .visible(() -> this.tone.get() == MarineLayer.Tone.Custom)
            .build()
      );
   private final Setting<Double> sunGlow = this.sgLook
      .add(
         new DoubleSetting.Builder()
            .name("sun-glow")
            .description("How much the fog lights up where the sun or moon is behind it.")
            .defaultValue(1.0)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Boolean> coastalOnly = this.sgWhere
      .add(
         new BoolSetting.Builder()
            .name("follow-the-coast")
            .description("Heaviest over oceans, beaches and rivers, thinning as you go inland. Off, it is the same everywhere.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> inland = this.sgWhere
      .add(
         new DoubleSetting.Builder()
            .name("inland-amount")
            .description("How much of the fog is left away from the water.")
            .defaultValue(0.35)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.coastalOnly::get)
            .build()
      );
   private final Setting<Boolean> rainThickens = this.sgWhere
      .add(new BoolSetting.Builder().name("thicker-in-rain").description("Rain pulls the fog in closer and thickens it.").defaultValue(true).build());
   private final Tween presence = Tween.progress(Spec.WORLD_FADE, false);
   private final Spring sDensity = spring();
   private final Spring sCover = spring();
   private final Spring sHeight = spring();
   private final Spring sStart = spring();
   private final Spring sPatch = spring();
   private final Spring sPatchSize = spring();
   private final Spring sBase = spring();
   private final Spring coast = new Spring(6.0, Channel.WORLD, 1.0);
   private float coastTarget = 1.0F;
   private boolean primed;
   private int tick;
   private double driftX;
   private double driftZ;
   private double churn;
   private double lastFrame = -1.0;

   private static Spring spring() {
      return new Spring(1.6, Channel.WORLD, 0.0);
   }

   public MarineLayer() {
      super(
         SpecterClientAddon.VISUALS,
         "marine-layer",
         "A low bank of sea fog over the coast: thick by the water, thinning with height, drifting, glowing toward the sun. Only you see it."
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
      if (this.mc.level != null && this.mc.player != null && ++this.tick % 20 == 0) {
         if (!this.coastalOnly.get()) {
            this.coastTarget = 1.0F;
         } else {
            BlockPos here = BlockPos.containing(this.mc.gameRenderer.mainCamera().position());
            int wet = 0;
            int looked = 0;
            int[][] probes = new int[][]{{0, 0}, {48, 0}, {-48, 0}, {0, 48}, {0, -48}, {96, 96}, {-96, 96}, {96, -96}, {-96, -96}};

            for (int[] probe : probes) {
               BlockPos at = new BlockPos(here.getX() + probe[0], 63, here.getZ() + probe[1]);
               if (this.mc.level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
                  looked++;
                  Holder<Biome> biome = this.mc.level.getBiome(at);
                  if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)) {
                     wet++;
                  }
               }
            }

            if (looked > 0) {
               this.coastTarget = Math.min(1.0F, wet / (looked * 0.45F));
            }
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
         this.primed = false;
      } else if (p.overworld) {
         MarineLayer.Preset chosen = this.preset.get();
         boolean custom = chosen == MarineLayer.Preset.Custom;
         double tDensity = custom ? this.density.get() : chosen.density;
         double tCover = custom ? this.opacity.get() : chosen.cover;
         double tHeight = custom ? this.height.get().intValue() : chosen.height;
         double tStart = custom ? this.distance.get().intValue() : chosen.start;
         double tPatch = custom ? this.patchiness.get() : chosen.patchiness;
         double tPatchSize = custom ? this.patchSize.get().intValue() : chosen.patchSize;
         if (!this.primed) {
            this.primed = true;
            this.sDensity.set(tDensity);
            this.sCover.set(tCover);
            this.sHeight.set(tHeight);
            this.sStart.set(tStart);
            this.sPatch.set(tPatch);
            this.sPatchSize.set(tPatchSize);
            this.sBase.set(this.base.get().intValue());
            this.coast.set(this.coastTarget);
         }

         float near = (float)this.coast.follow(this.coastTarget);
         float where = this.coastalOnly.get() ? this.inland.get().floatValue() + (1.0F - this.inland.get().floatValue()) * near : 1.0F;
         float wet = this.rainThickens.get() ? p.rain : 0.0F;
         p.fog = true;
         p.fogStrength = shown;
         p.fogDensity = (float)(this.sDensity.follow(tDensity) * this.amount.get() * where * (1.0 + wet * 0.9));
         p.fogMaxCover = (float)Math.min(1.0, this.sCover.follow(tCover) * (0.6 + 0.4 * where) + wet * 0.05);
         p.fogFalloff = (float)Math.max(0.5, this.sHeight.follow(tHeight) / 4.0);
         p.fogBase = (float)this.sBase.follow(this.base.get().intValue());
         p.fogStart = (float)(this.sStart.follow(tStart) * (1.0 - wet * 0.5));
         p.fogPatchiness = (float)this.sPatch.follow(tPatch);
         p.fogPatchSize = (float)this.sPatchSize.follow(tPatchSize);
         p.fogSunGlow = this.sunGlow.get().floatValue();
         p.fogSkyReach = Math.max(600.0F, p.renderDistance * 3.0F);
         double now = Motion.now();
         double dt = this.lastFrame < 0.0 ? 0.0 : Math.min(0.1, now - this.lastFrame);
         this.lastFrame = now;
         double speed = this.drift.get() * 2.2 / Math.max(8.0F, p.fogPatchSize);
         this.driftX = wrap(this.driftX + dt * speed * 0.92);
         this.driftZ = wrap(this.driftZ + dt * speed * 0.39);
         this.churn = wrap(this.churn + dt * this.drift.get() * 0.004);
         p.fogDriftX = (float)this.driftX;
         p.fogDriftZ = (float)this.driftZ;
         p.fogChurn = (float)this.churn;
         p.fogCamU = (float)wrap(p.worldX / Math.max(8.0F, p.fogPatchSize));
         p.fogCamV = (float)wrap(p.worldZ / Math.max(8.0F, p.fogPatchSize));
         this.color(p);
      }
   }

   private static double wrap(double v) {
      return v - Math.floor(v);
   }

   private void color(FxParams p) {
      if (this.tone.get() == MarineLayer.Tone.Custom) {
         SettingColor c = this.customColor.get();
         float light = 0.16F + 0.84F * (1.0F - p.night);
         p.fogColor[0] = c.r / 255.0F * light;
         p.fogColor[1] = c.g / 255.0F * light;
         p.fogColor[2] = c.b / 255.0F * light;
      } else {
         float lowSun = (float)(Math.clamp(1.0 - Math.abs(p.sunY) / 0.32, 0.0, 1.0) * (1.0 - p.night * 0.7));
         float[] day = new float[]{0.84F, 0.88F, 0.93F};
         float[] warm = new float[]{0.98F, 0.76F, 0.62F};
         float[] nightCol = new float[]{0.075F + 0.04F * p.moonBright, 0.095F + 0.05F * p.moonBright, 0.15F + 0.06F * p.moonBright};

         for (int i = 0; i < 3; i++) {
            float lit = day[i] + (warm[i] - day[i]) * lowSun * 0.75F;
            p.fogColor[i] = lit + (nightCol[i] - lit) * p.night;
            float grey = 0.62F - 0.4F * p.night;
            p.fogColor[i] = p.fogColor[i] + (grey - p.fogColor[i]) * p.rain * 0.45F;
         }

         if (Atmosphere.drawsSky() && SkyRenderer.fresh()) {
            float yaw = this.mc.gameRenderer.mainCamera().yRot() * 0.017453292F;
            int sky = SkyModel.horizonColor(SkyRenderer.params(), (float)(-Math.sin(yaw)), (float)Math.cos(yaw));
            float[] horizon = new float[]{(sky >> 16 & 0xFF) / 255.0F, (sky >> 8 & 0xFF) / 255.0F, (sky & 0xFF) / 255.0F};

            for (int i = 0; i < 3; i++) {
               p.fogColor[i] = p.fogColor[i] + (horizon[i] - p.fogColor[i]) * 0.45F;
            }
         }
      }
   }

   public static enum Preset {
      Haze("Light Haze", 0.006, 0.55, 26.0, 70.0, 0.35, 220.0),
      Mist("Morning Mist", 0.016, 0.72, 14.0, 28.0, 0.55, 150.0),
      Coastal("Coastal Fog", 0.028, 0.86, 20.0, 20.0, 0.5, 170.0),
      Bank("Dense Bank", 0.06, 0.97, 16.0, 10.0, 0.3, 200.0),
      Custom("Custom", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

      private final String title;
      final double density;
      final double cover;
      final double height;
      final double start;
      final double patchiness;
      final double patchSize;

      private Preset(String title, double density, double cover, double height, double start, double patchiness, double patchSize) {
         this.title = title;
         this.density = density;
         this.cover = cover;
         this.height = height;
         this.start = start;
         this.patchiness = patchiness;
         this.patchSize = patchSize;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Tone {
      Natural("Natural"),
      Custom("Custom");

      private final String title;

      private Tone(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
