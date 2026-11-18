package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.sky.SkyModel;
import com.jfant.specter.sky.SkyParams;
import com.jfant.specter.sky.SkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;

public class Atmosphere extends Module {
   private static Atmosphere instance;
   private static volatile boolean on;
   private final SettingGroup sgSky = this.settings.getDefaultGroup();
   private final SettingGroup sgSun = this.settings.createGroup("Sunrise & Sunset");
   private final SettingGroup sgClouds = this.settings.createGroup("Clouds");
   private final SettingGroup sgWeather = this.settings.createGroup("Fog & Weather");
   private final SettingGroup sgColors = this.settings.createGroup("Custom Colors");
   private final Setting<Boolean> sky = this.sgSky
      .add(
         new BoolSetting.Builder()
            .name("sky")
            .description("Draw Specter's sky: blue that deepens overhead, haze at the horizon, a soft glow round the sun.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> saturation = this.sgSky
      .add(
         new DoubleSetting.Builder()
            .name("sky-saturation")
            .description("How rich the sky and cloud colours are.")
            .defaultValue(1.15)
            .range(0.0, 2.0)
            .sliderRange(0.0, 2.0)
            .build()
      );
   private final Setting<Double> brightness = this.sgSky
      .add(
         new DoubleSetting.Builder()
            .name("sky-brightness")
            .description("How bright the sky and clouds are.")
            .defaultValue(1.0)
            .range(0.4, 1.6)
            .sliderRange(0.4, 1.6)
            .build()
      );
   private final Setting<Double> haze = this.sgSky
      .add(
         new DoubleSetting.Builder()
            .name("haze")
            .description("How much haze is in the air: low is a crisp deep blue, high is milky and soft.")
            .defaultValue(1.0)
            .range(0.2, 3.0)
            .sliderRange(0.2, 3.0)
            .build()
      );
   private final Setting<Double> sunGlow = this.sgSky
      .add(
         new DoubleSetting.Builder()
            .name("sun-glow")
            .description("The bright halo round the sun.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .build()
      );
   private final Setting<Double> sunsetIntensity = this.sgSun
      .add(
         new DoubleSetting.Builder()
            .name("sunset-intensity")
            .description("Gold, pink and violet along the horizon as the sun goes down.")
            .defaultValue(1.0)
            .range(0.0, 2.5)
            .sliderRange(0.0, 2.5)
            .build()
      );
   private final Setting<Double> sunriseIntensity = this.sgSun
      .add(
         new DoubleSetting.Builder()
            .name("sunrise-intensity")
            .description("The same colours as the sun comes up.")
            .defaultValue(0.85)
            .range(0.0, 2.5)
            .sliderRange(0.0, 2.5)
            .build()
      );
   private final Setting<Boolean> clouds = this.sgClouds
      .add(
         new BoolSetting.Builder()
            .name("clouds")
            .description("Soft, shaded clouds drifting across the sky, glowing at sunset. Replaces vanilla's block clouds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Atmosphere.Quality> cloudQuality = this.sgClouds
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("cloud-quality"))
                     .description("Low: flat and cheap. Medium: shaded. High: soft self-shadowing. Ultra: the most detail; heaviest."))
                  .defaultValue(Atmosphere.Quality.High))
               .visible(this.clouds::get))
            .build()
      );
   private final Setting<Atmosphere.Style> cloudStyle = this.sgClouds
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("cloud-appearance"))
                     .description(
                        "The kind of cloud. Cumulus: full fair-weather heaps. Towering: tall, with thick cores that climb. Stratus: low soft sheets. Wispy: thin and ragged. Changing it eases from one to the other."
                     ))
                  .defaultValue(Atmosphere.Style.Cumulus))
               .visible(this.clouds::get))
            .build()
      );
   private final Setting<Atmosphere.Size> cloudSize = this.sgClouds
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("cloud-size"))
                     .description("How big the cloud shapes are across the sky."))
                  .defaultValue(Atmosphere.Size.Medium))
               .visible(this.clouds::get))
            .build()
      );
   private final Setting<Double> cloudDetail = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("cloud-detail")
            .description(
               "How deeply billows are carved into the sides and tops. Low is smooth, high is cauliflower. The billows slowly churn, so clouds change shape as they drift."
            )
            .defaultValue(1.0)
            .range(0.0, 1.5)
            .sliderRange(0.0, 1.5)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Integer> windDirection = this.sgClouds
      .add(
         new IntSetting.Builder()
            .name("wind-direction")
            .description("Which way the clouds drift, in degrees: 0 east, 90 south, 180 west, 270 north.")
            .defaultValue(15)
            .range(0, 359)
            .sliderRange(0, 359)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Double> cloudDensity = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("cloud-density")
            .description("How much of the sky is cloud: a few fair-weather puffs up to nearly overcast.")
            .defaultValue(0.45)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Double> cloudOpacity = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("cloud-opacity")
            .description("How solid the clouds look.")
            .defaultValue(1.0)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Integer> cloudHeight = this.sgClouds
      .add(
         new IntSetting.Builder()
            .name("cloud-height")
            .description("The height the bottoms of the clouds sit at. They thin out as you fly up to them.")
            .defaultValue(260)
            .range(120, 640)
            .sliderRange(120, 640)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Double> cloudSpeed = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("cloud-speed")
            .description("How fast the clouds drift.")
            .defaultValue(1.0)
            .range(0.0, 10.0)
            .sliderRange(0.0, 5.0)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Double> highClouds = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("high-clouds")
            .description("Thin wispy streaks high above the main clouds.")
            .defaultValue(0.4)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Boolean> cloudShadows = this.sgClouds
      .add(
         new BoolSetting.Builder()
            .name("cloud-shadows")
            .description("Shadows of the clouds drifting over the ground in sunlight.")
            .defaultValue(true)
            .visible(this.clouds::get)
            .build()
      );
   private final Setting<Double> shadowStrength = this.sgClouds
      .add(
         new DoubleSetting.Builder()
            .name("shadow-strength")
            .description("How dark the cloud shadows are.")
            .defaultValue(0.55)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(() -> this.clouds.get() && this.cloudShadows.get())
            .build()
      );
   private final Setting<Boolean> vanillaClouds = this.sgClouds
      .add(
         new BoolSetting.Builder()
            .name("keep-vanilla-clouds")
            .description("Keep Minecraft's block clouds as well (at the cloud height above).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> atmosphericFog = this.sgWeather
      .add(
         new DoubleSetting.Builder()
            .name("atmospheric-fog")
            .description("How far you see before the land fades into the sky. Low is clear and far, high is misty.")
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .build()
      );
   private final Setting<Boolean> weather = this.sgWeather
      .add(
         new BoolSetting.Builder()
            .name("weather-effects")
            .description("Rain greys the sky, thickens the clouds into storm clouds and pulls the fog in; lightning lights up the clouds.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> customColors = this.sgColors
      .add(new BoolSetting.Builder().name("custom-sky-colors").description("Mix your own colours into the sky.").defaultValue(false).build());
   private final Setting<Double> customMix = this.sgColors
      .add(
         new DoubleSetting.Builder()
            .name("color-mix")
            .description("How much of your colours replaces the real sky's.")
            .defaultValue(0.6)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.customColors::get)
            .build()
      );
   private final Setting<SettingColor> zenithColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("zenith-color")
            .description("The sky straight up.")
            .defaultValue(new SettingColor(64, 70, 210))
            .visible(this.customColors::get)
            .build()
      );
   private final Setting<SettingColor> horizonColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("horizon-color")
            .description("The sky low down.")
            .defaultValue(new SettingColor(170, 150, 255))
            .visible(this.customColors::get)
            .build()
      );
   private final Setting<SettingColor> sunsetColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("sunset-color")
            .description("The glow by the sun at sunrise and sunset.")
            .defaultValue(new SettingColor(255, 110, 170))
            .visible(this.customColors::get)
            .build()
      );
   private double windX;
   private double windZ;
   private double cirrusX;
   private double cirrusZ;
   private long lastFrame;
   private final Spring sSoft = lookSpring();
   private final Spring sTower = lookSpring();
   private final Spring sThick = lookSpring();
   private final Spring sDetail = lookSpring();
   private boolean lookPrimed;
   private int fogColor;
   private long fogColorFrame = -1L;

   private static Spring lookSpring() {
      return new Spring(2.2, Channel.WORLD, 0.0);
   }

   public Atmosphere() {
      super(
         SpecterClientAddon.VISUALS, "atmosphere", "A new sky: deep blue days, cinematic sunsets, soft drifting clouds with shadows, weather. Only you see it."
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      on = true;
      this.lastFrame = 0L;
   }

   @Override
   public void onDeactivate() {
      on = false;
      SkyRenderer.release();
   }

   public static boolean on() {
      return on;
   }

   public static boolean drawsSky() {
      return on && instance.sky.get();
   }

   public static boolean drawsClouds() {
      return on && instance.clouds.get();
   }

   public static boolean hidesVanillaClouds() {
      return on && instance.clouds.get() && !instance.vanillaClouds.get();
   }

   public static long cloudTime(long worldTime) {
      return on ? (long)(worldTime * instance.cloudSpeed.get()) : worldTime;
   }

   public static void fill(SkyParams p) {
      if (on) {
         instance.fillParams(p);
      }
   }

   private void fillParams(SkyParams p) {
      long now = System.nanoTime();
      double dt = this.lastFrame == 0L ? 0.0 : Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      if (!this.weather.get()) {
         p.rain = 0.0F;
         p.thunder = 0.0F;
         p.flash = 0.0F;
      }

      p.atmosphereSky = this.sky.get();
      p.saturation = this.saturation.get().floatValue();
      p.brightness = this.brightness.get().floatValue();
      p.haze = this.haze.get().floatValue();
      p.sunGlow = this.sunGlow.get().floatValue();
      p.morning = p.sunX > 0.0F;
      p.sunsetStrength = (p.morning ? this.sunriseIntensity : this.sunsetIntensity).get().floatValue();
      p.exposure = 1.0F;
      p.fog = this.atmosphericFog.get().floatValue();
      p.horizonBlend = 1.0F;
      if (this.customColors.get()) {
         p.customMix = this.customMix.get().floatValue();
         linear(this.zenithColor.get(), p.zenith);
         linear(this.horizonColor.get(), p.horizon);
         linear(this.sunsetColor.get(), p.sunset);
      }

      if (this.clouds.get()) {
         Atmosphere.Quality q = this.cloudQuality.get();
         p.lowClouds = true;
         p.cloudQuality = q.level;
         p.steps = q.steps;
         p.coverage = (float)Mth.clamp(this.cloudDensity.get() + p.rain * 0.38 + p.thunder * 0.15, 0.0, 0.97);
         Atmosphere.Style style = this.cloudStyle.get();
         if (!this.lookPrimed) {
            this.lookPrimed = true;
            this.sSoft.set(style.softness);
            this.sTower.set(style.tower);
            this.sThick.set(style.thickness);
            this.sDetail.set(this.cloudDetail.get());
         }

         p.cloudSoftness = (float)this.sSoft.follow(style.softness);
         p.cloudTower = (float)this.sTower.follow(style.tower);
         p.cloudDetail = (float)this.sDetail.follow(this.cloudDetail.get());
         p.cloudScale = (float)this.cloudSize.get().scale;
         p.thickness = (float)this.sThick.follow(style.thickness) * (q.level >= 2 ? 1.0F : 0.8F);
         float above = this.cloudHeight.get().intValue() - p.camY;
         p.opacity = this.cloudOpacity.get().floatValue() * SkyRenderer.smoothstep(15.0F, 70.0F, above);
         p.cloudHeight = Math.max(above, 40.0F);
         p.cirrus = this.highClouds.get().floatValue();
         p.shadow = this.cloudShadows.get() ? this.shadowStrength.get().floatValue() : 0.0F;
         double speed = this.cloudSpeed.get() * 5.0;
         double heading = Math.toRadians(this.windDirection.get().intValue());
         double wx = Math.cos(heading);
         double wz = Math.sin(heading);
         this.windX = wrap(this.windX + dt * speed * wx);
         this.windZ = wrap(this.windZ + dt * speed * wz);
         this.cirrusX = wrap(this.cirrusX + dt * speed * 1.6 * (wx * 0.8 - wz * 0.6));
         this.cirrusZ = wrap(this.cirrusZ + dt * speed * 1.6 * (wz * 0.8 + wx * 0.6));
         p.windX = (float)this.windX;
         p.windZ = (float)this.windZ;
         p.cirrusX = (float)this.cirrusX;
         p.cirrusZ = (float)this.cirrusZ;
      }
   }

   private static double wrap(double v) {
      return v - Math.floor(v / 10240.0) * 10240.0;
   }

   private static void linear(SettingColor c, float[] out) {
      out[0] = (float)Math.pow(c.r / 255.0, 2.2);
      out[1] = (float)Math.pow(c.g / 255.0, 2.2);
      out[2] = (float)Math.pow(c.b / 255.0, 2.2);
   }

   public static <V> V override(EnvironmentAttribute<V> attribute, V value) {
      return (V)(on && value != null ? instance.adjust(attribute, value) : value);
   }

   private Object adjust(EnvironmentAttribute<?> attribute, Object value) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level.dimensionType().skybox() == Skybox.OVERWORLD) {
         boolean ourSky = this.sky.get() && SkyRenderer.fresh();
         if (!ourSky || attribute != EnvironmentAttributes.FOG_COLOR && attribute != EnvironmentAttributes.SKY_COLOR) {
            if (ourSky && attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
               return (Integer)value & 16777215;
            } else if (attribute == EnvironmentAttributes.CLOUD_HEIGHT && this.clouds.get()) {
               return (float)this.cloudHeight.get().intValue();
            } else if (attribute != EnvironmentAttributes.FOG_START_DISTANCE
               && attribute != EnvironmentAttributes.FOG_END_DISTANCE
               && attribute != EnvironmentAttributes.SKY_FOG_END_DISTANCE
               && attribute != EnvironmentAttributes.CLOUD_FOG_END_DISTANCE) {
               return value;
            } else {
               double scale = 1.45 - this.atmosphericFog.get() * 0.95;
               if (this.weather.get()) {
                  scale *= 1.0 - 0.35 * mc.level.getRainLevel(1.0F);
               }

               return (float)(((Float)value).floatValue() * scale);
            }
         } else {
            return (Integer)value & 0xFF000000 | this.horizonFogColor();
         }
      } else {
         return value;
      }
   }

   private int horizonFogColor() {
      Minecraft mc = Minecraft.getInstance();
      long now = System.nanoTime();
      if (now - this.fogColorFrame > 4000000L || this.fogColorFrame < 0L) {
         this.fogColorFrame = now;
         Camera camera = mc.gameRenderer.getMainCamera();
         float yaw = camera == null ? 0.0F : camera.yRot() * 0.017453292F;
         this.fogColor = SkyModel.horizonColor(SkyRenderer.params(), -Mth.sin(yaw), Mth.cos(yaw));
      }

      return this.fogColor;
   }

   public static enum Quality {
      Low("Low", 0, 1),
      Medium("Medium", 1, 8),
      High("High", 2, 14),
      Ultra("Ultra", 3, 24);

      private final String title;
      final int level;
      final int steps;

      private Quality(String title, int level, int steps) {
         this.title = title;
         this.level = level;
         this.steps = steps;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Size {
      Small("Small", 2.0),
      Medium("Medium", 1.0),
      Large("Large", 0.5);

      private final String title;
      final double scale;

      private Size(String title, double scale) {
         this.title = title;
         this.scale = scale;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Style {
      Cumulus("Cumulus", 0.3, 0.5, 230.0),
      Towering("Towering", 0.22, 1.0, 340.0),
      Stratus("Stratus", 0.95, 0.1, 110.0),
      Wispy("Wispy", 0.7, 0.3, 150.0);

      private final String title;
      final double softness;
      final double tower;
      final double thickness;

      private Style(String title, double softness, double tower, double thickness) {
         this.title = title;
         this.softness = softness;
         this.tower = tower;
         this.thickness = thickness;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
