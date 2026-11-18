package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.sky.SkyParams;
import com.jfant.specter.sky.SkyRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class NebulaNight extends Module {
   private static NebulaNight instance;
   private static volatile boolean on;
   private static final Matrix4f GALAXY = new Matrix4f().rotationY((float)Math.toRadians(-60.0)).rotateZ((float)Math.toRadians(27.1)).rotateX(1.5707964F);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgStars = this.settings.createGroup("Stars");
   private final SettingGroup sgGalaxy = this.settings.createGroup("Milky Way & Nebulae");
   private final SettingGroup sgMeteors = this.settings.createGroup("Shooting Stars");
   private final SettingGroup sgExtras = this.settings.createGroup("Aurora & Extras");
   private final Setting<NebulaNight.Quality> quality = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("quality"))
                  .description(
                     "Low: two star layers, no aurora or dust. Medium: dense stars in the Milky Way. High: finest stars, smoother aurora. Ultra: the most aurora detail."
                  ))
               .defaultValue(NebulaNight.Quality.High))
            .build()
      );
   private final Setting<Double> skyBrightness = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("sky-brightness")
            .description("Brightness of the whole night sky.")
            .defaultValue(1.0)
            .range(0.2, 2.5)
            .sliderRange(0.2, 2.5)
            .build()
      );
   private final Setting<Boolean> stars = this.sgStars
      .add(
         new BoolSetting.Builder()
            .name("stars")
            .description("Thousands of stars in many sizes, colours and brightnesses (vanilla's are replaced).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> starDensity = this.sgStars
      .add(
         new DoubleSetting.Builder()
            .name("star-density")
            .description("How many stars there are.")
            .defaultValue(1.0)
            .range(0.1, 3.0)
            .sliderRange(0.1, 3.0)
            .visible(this.stars::get)
            .build()
      );
   private final Setting<Double> starBrightness = this.sgStars
      .add(
         new DoubleSetting.Builder()
            .name("star-brightness")
            .description("How bright the stars are.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .visible(this.stars::get)
            .build()
      );
   private final Setting<Double> twinkle = this.sgStars
      .add(
         new DoubleSetting.Builder()
            .name("star-twinkle")
            .description("How much the stars twinkle (more near the horizon, as through real air).")
            .defaultValue(0.6)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.stars::get)
            .build()
      );
   private final Setting<Boolean> cosmicDust = this.sgStars
      .add(new BoolSetting.Builder().name("cosmic-dust").description("Faint violet motes that drift a little against the stars.").defaultValue(true).build());
   private final Setting<Boolean> milkyWay = this.sgGalaxy
      .add(
         new BoolSetting.Builder()
            .name("milky-way")
            .description("The Milky Way arching over the sky: its bright core, star clouds, dark dust lanes and distant galaxies.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> milkyWayIntensity = this.sgGalaxy
      .add(
         new DoubleSetting.Builder()
            .name("milky-way-intensity")
            .description("How bright the Milky Way is.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .visible(this.milkyWay::get)
            .build()
      );
   private final Setting<Boolean> nebulae = this.sgGalaxy
      .add(
         new BoolSetting.Builder()
            .name("nebulae")
            .description("Great glowing clouds of gas with bright filaments and dark pillars.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> nebulaIntensity = this.sgGalaxy
      .add(
         new DoubleSetting.Builder()
            .name("nebula-intensity")
            .description("How bright the nebulae glow.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .visible(this.nebulae::get)
            .build()
      );
   private final Setting<NebulaNight.Palette> nebulaColor = this.sgGalaxy
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("nebula-color"))
                     .description("The two colours the nebulae glow in."))
                  .defaultValue(NebulaNight.Palette.Specter))
               .visible(this.nebulae::get))
            .build()
      );
   private final Setting<SettingColor> firstColor = this.sgGalaxy
      .add(
         new ColorSetting.Builder()
            .name("first-color")
            .description("The nebulae's main colour.")
            .defaultValue(new SettingColor(158, 51, 255))
            .visible(() -> this.nebulae.get() && this.nebulaColor.get() == NebulaNight.Palette.Custom)
            .build()
      );
   private final Setting<SettingColor> secondColor = this.sgGalaxy
      .add(
         new ColorSetting.Builder()
            .name("second-color")
            .description("The colour of their outer glow and second filaments.")
            .defaultValue(new SettingColor(31, 107, 255))
            .visible(() -> this.nebulae.get() && this.nebulaColor.get() == NebulaNight.Palette.Custom)
            .build()
      );
   private final Setting<Boolean> shootingStars = this.sgMeteors
      .add(
         new BoolSetting.Builder()
            .name("shooting-stars")
            .description("Meteors now and then: a bright head and a long fading trail.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> frequency = this.sgMeteors
      .add(
         new DoubleSetting.Builder()
            .name("shooting-star-frequency")
            .description("About how many per minute.")
            .defaultValue(2.5)
            .range(0.1, 30.0)
            .sliderRange(0.1, 15.0)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Double> meteorSpeed = this.sgMeteors
      .add(
         new DoubleSetting.Builder()
            .name("shooting-star-speed")
            .description("How fast they cross the sky.")
            .defaultValue(1.0)
            .range(0.25, 3.0)
            .sliderRange(0.25, 3.0)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Double> meteorSize = this.sgMeteors
      .add(
         new DoubleSetting.Builder()
            .name("shooting-star-size")
            .description("How big and bright their heads are.")
            .defaultValue(1.0)
            .range(0.3, 3.0)
            .sliderRange(0.3, 3.0)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Double> trailLength = this.sgMeteors
      .add(
         new DoubleSetting.Builder()
            .name("meteor-trail-length")
            .description("How long their trails are.")
            .defaultValue(1.0)
            .range(0.2, 2.0)
            .sliderRange(0.2, 2.0)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Boolean> fireballs = this.sgMeteors
      .add(
         new BoolSetting.Builder()
            .name("fireballs")
            .description("Rarely, a spectacular one: big, slow, flaring, leaving a glowing train that lingers.")
            .defaultValue(true)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Boolean> showers = this.sgMeteors
      .add(
         new BoolSetting.Builder()
            .name("meteor-showers")
            .description("Once in a while a short burst of several, all streaming from one point in the sky.")
            .defaultValue(true)
            .visible(this.shootingStars::get)
            .build()
      );
   private final Setting<Boolean> aurora = this.sgExtras
      .add(
         new BoolSetting.Builder()
            .name("aurora")
            .description("Curtains of green, blue and violet light rippling over the northern sky.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> auroraIntensity = this.sgExtras
      .add(
         new DoubleSetting.Builder()
            .name("aurora-intensity")
            .description("How bright the aurora is.")
            .defaultValue(1.0)
            .range(0.1, 3.0)
            .sliderRange(0.1, 3.0)
            .visible(this.aurora::get)
            .build()
      );
   private final Setting<Boolean> constellations = this.sgExtras
      .add(
         new BoolSetting.Builder()
            .name("constellations")
            .description("Faint lines joining the stars of real constellations: the Big Dipper, Orion, Cassiopeia, Cygnus, Lyra and the Southern Cross.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Double> constellationBrightness = this.sgExtras
      .add(
         new DoubleSetting.Builder()
            .name("constellation-brightness")
            .description("How bright the lines and their stars are.")
            .defaultValue(0.6)
            .range(0.1, 1.0)
            .sliderRange(0.1, 1.0)
            .visible(this.constellations::get)
            .build()
      );
   private final Setting<Boolean> horizonGlow = this.sgExtras
      .add(new BoolSetting.Builder().name("horizon-glow").description("A soft violet and blue glow along the horizon.").defaultValue(true).build());
   private final Setting<Double> horizonGlowIntensity = this.sgExtras
      .add(
         new DoubleSetting.Builder()
            .name("horizon-glow-intensity")
            .description("How strong the horizon glow is.")
            .defaultValue(1.0)
            .range(0.0, 3.0)
            .sliderRange(0.0, 3.0)
            .visible(this.horizonGlow::get)
            .build()
      );
   private final List<NebulaNight.Meteor> meteors = new ArrayList<>();
   private final Random random = new Random();
   private long lastFrame;
   private int showerLeft;
   private float showerGap;
   private final Vector3f radiant = new Vector3f();
   private final float[] lineData = buildConstellations();

   public NebulaNight() {
      super(
         SpecterClientAddon.VISUALS, "nebula-night", "A cinematic night: the Milky Way, glowing nebulae, twinkling stars and shooting stars. Only you see it."
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      on = true;
      this.lastFrame = 0L;
      this.meteors.clear();
      this.showerLeft = 0;
   }

   @Override
   public void onDeactivate() {
      on = false;
      this.meteors.clear();
      SkyRenderer.release();
   }

   public static boolean on() {
      return on;
   }

   public static boolean drawsStars() {
      return on && instance.stars.get();
   }

   public static Matrix4f galaxyFrame() {
      return GALAXY;
   }

   public static void fill(SkyParams p) {
      if (on) {
         instance.fillParams(p);
      }
   }

   private void fillParams(SkyParams p) {
      long now = System.nanoTime();
      float dt = this.lastFrame == 0L ? 0.0F : (float)Math.min(0.1, (now - this.lastFrame) / 1.0E9);
      this.lastFrame = now;
      p.nebula = true;
      p.nightQuality = this.quality.get().level;
      p.skyBrightness = this.skyBrightness.get().floatValue();
      p.stars = this.stars.get();
      p.density = this.starDensity.get().floatValue();
      p.starBrightness = this.starBrightness.get().floatValue();
      p.twinkle = this.twinkle.get().floatValue();
      p.dust = this.cosmicDust.get() ? 1.0F : 0.0F;
      p.milkyWay = this.milkyWay.get() ? this.milkyWayIntensity.get().floatValue() : 0.0F;
      p.nebulaIntensity = this.nebulae.get() ? this.nebulaIntensity.get().floatValue() : 0.0F;
      NebulaNight.Palette palette = this.nebulaColor.get();
      if (palette == NebulaNight.Palette.Custom) {
         linear(this.firstColor.get().r << 16 | this.firstColor.get().g << 8 | this.firstColor.get().b, p.colorA);
         linear(this.secondColor.get().r << 16 | this.secondColor.get().g << 8 | this.secondColor.get().b, p.colorB);
      } else {
         linear(palette.first, p.colorA);
         linear(palette.second, p.colorB);
      }

      p.horizonGlow = this.horizonGlow.get() ? this.horizonGlowIntensity.get().floatValue() : 0.0F;
      p.aurora = this.aurora.get() ? this.auroraIntensity.get().floatValue() : 0.0F;
      if (this.constellations.get()) {
         p.constellations = this.constellationBrightness.get().floatValue();
         p.lineCount = this.lineData.length / 8;
         System.arraycopy(this.lineData, 0, p.lines, 0, this.lineData.length);
      }

      this.updateMeteors(p, dt);
   }

   private static void linear(int rgb, float[] out) {
      out[0] = (float)Math.pow((rgb >> 16 & 0xFF) / 255.0, 2.2);
      out[1] = (float)Math.pow((rgb >> 8 & 0xFF) / 255.0, 2.2);
      out[2] = (float)Math.pow((rgb & 0xFF) / 255.0, 2.2);
   }

   private void updateMeteors(SkyParams p, float dt) {
      boolean dark = p.night > 0.35F && p.rain < 0.5F;
      if (this.shootingStars.get() && dark && dt > 0.0F) {
         if (this.showerLeft > 0) {
            this.showerGap -= dt;
            if (this.showerGap <= 0.0F && this.meteors.size() < 6) {
               this.spawn(false, this.radiant);
               this.showerLeft--;
               this.showerGap = 0.25F + this.random.nextFloat() * 0.9F;
            }
         } else if (this.random.nextFloat() < this.frequency.get() / 60.0 * dt && this.meteors.size() < 6) {
            if (this.showers.get() && this.random.nextFloat() < 0.04F) {
               this.randomSkyDirection(this.radiant, 0.35F, 0.9F);
               this.showerLeft = 3 + this.random.nextInt(5);
               this.showerGap = 0.0F;
            } else {
               this.spawn(this.fireballs.get() && this.random.nextFloat() < 0.05F, null);
            }
         }
      }

      if (!this.shootingStars.get()) {
         this.meteors.clear();
      }

      int count = 0;

      for (int i = this.meteors.size() - 1; i >= 0; i--) {
         NebulaNight.Meteor m = this.meteors.get(i);
         m.age += dt;
         if (m.age > m.duration + m.linger) {
            this.meteors.remove(i);
         }
      }

      float[] out = p.meteors;

      for (NebulaNight.Meteor m : this.meteors) {
         if (count >= 6) {
            break;
         }

         float progress = m.age / m.duration;
         float brightness = m.brightness;
         if (m.fireball) {
            brightness *= 1.0F + 1.6F * SkyRenderer.smoothstep(0.55F, 0.8F, progress) * (1.0F - SkyRenderer.smoothstep(0.82F, 0.96F, progress));
         }

         int o = count * 16;
         out[o] = m.start.x;
         out[o + 1] = m.start.y;
         out[o + 2] = m.start.z;
         out[o + 3] = progress;
         out[o + 4] = m.end.x;
         out[o + 5] = m.end.y;
         out[o + 6] = m.end.z;
         out[o + 7] = m.width;
         out[o + 8] = m.r;
         out[o + 9] = m.g;
         out[o + 10] = m.b;
         out[o + 11] = brightness * Math.min(1.0F, p.night * 1.4F);
         out[o + 12] = m.trail;
         out[o + 13] = m.fireball ? 1.0F : 0.0F;
         out[o + 14] = 0.0F;
         out[o + 15] = 0.0F;
         count++;
      }

      p.meteorCount = count;
   }

   private void spawn(boolean fireball, Vector3f from) {
      NebulaNight.Meteor m = new NebulaNight.Meteor();
      m.fireball = fireball;
      Vector3f down = new Vector3f();
      if (from != null) {
         this.randomSkyDirection(m.start, 0.25F, 0.85F);
         m.start.lerp(from, 0.55F).normalize();
         down.set(m.start).sub(from);
         if (down.lengthSquared() < 1.0E-6F) {
            down.set(0.0F, -1.0F, 0.0F);
         }
      } else {
         this.randomSkyDirection(m.start, 0.3F, 0.92F);
         Vector3f side = new Vector3f(m.start).cross(0.0F, 1.0F, 0.0F);
         if (side.lengthSquared() < 1.0E-6F) {
            side.set(1.0F, 0.0F, 0.0F);
         }

         side.normalize().mul(this.random.nextFloat() * 2.4F - 1.2F);
         down.set(0.0F, -1.0F, 0.0F).add(side);
      }

      Vector3f tangent = new Vector3f(down).sub(new Vector3f(m.start).mul(down.dot(m.start)));
      if (tangent.lengthSquared() < 1.0E-6F) {
         tangent.set(1.0F, 0.0F, 0.0F);
      }

      tangent.normalize();
      float span = (float)Math.toRadians(fireball ? 28.0F + this.random.nextFloat() * 30.0F : 8.0F + this.random.nextFloat() * 18.0F);
      m.end.set(m.start).mul((float)Math.cos(span)).add(new Vector3f(tangent).mul((float)Math.sin(span))).normalize();
      float degreesPerSecond = (fireball ? 13.0F : 22.0F + this.random.nextFloat() * 22.0F) * this.meteorSpeed.get().floatValue();
      m.duration = (float)Math.toDegrees(span) / degreesPerSecond;
      m.linger = fireball ? 3.0F : 0.15F;
      float size = this.meteorSize.get().floatValue();
      m.width = (fireball ? 0.0055F : 0.0012F + this.random.nextFloat() * 0.0012F) * size;
      m.brightness = (fireball ? 1.8F : 0.7F + this.random.nextFloat() * 0.8F) * (0.6F + 0.4F * size);
      m.trail = (float)Mth.clamp((fireball ? 0.75 : 0.35 + this.random.nextFloat() * 0.35) * this.trailLength.get(), 0.05, 1.0);
      float pick = this.random.nextFloat();
      if (fireball) {
         setColor(m, 0.62F, 1.0F, 0.76F);
      } else if (pick < 0.55F) {
         setColor(m, 0.78F, 0.86F, 1.0F);
      } else if (pick < 0.8F) {
         setColor(m, 0.82F, 0.62F, 1.0F);
      } else {
         setColor(m, 0.55F, 0.92F, 1.0F);
      }

      this.meteors.add(m);
   }

   private static void setColor(NebulaNight.Meteor m, float r, float g, float b) {
      m.r = r;
      m.g = g;
      m.b = b;
   }

   private void randomSkyDirection(Vector3f out, float minUp, float maxUp) {
      float y = minUp + this.random.nextFloat() * (maxUp - minUp);
      float a = this.random.nextFloat() * 6.2831855F;
      float h = (float)Math.sqrt(1.0F - y * y);
      out.set(h * Mth.cos(a), y, h * Mth.sin(a));
   }

   private static float[] buildConstellations() {
      double[][] segments = new double[][]{
         {11.062, 61.75, 11.031, 56.38, 0.8, 0.7},
         {11.031, 56.38, 11.897, 53.69, 0.7, 0.6},
         {11.897, 53.69, 12.257, 57.03, 0.6, 0.5},
         {12.257, 57.03, 11.062, 61.75, 0.5, 0.8},
         {12.257, 57.03, 12.9, 55.96, 0.5, 0.8},
         {12.9, 55.96, 13.399, 54.93, 0.8, 0.7},
         {13.399, 54.93, 13.792, 49.31, 0.7, 0.75},
         {5.585, 9.93, 5.919, 7.41, 0.45, 1.0},
         {5.585, 9.93, 5.419, 6.35, 0.45, 0.8},
         {5.919, 7.41, 5.679, -1.94, 1.0, 0.75},
         {5.419, 6.35, 5.533, -0.3, 0.8, 0.7},
         {5.679, -1.94, 5.604, -1.2, 0.75, 0.8},
         {5.604, -1.2, 5.533, -0.3, 0.8, 0.7},
         {5.679, -1.94, 5.796, -9.67, 0.75, 0.7},
         {5.533, -0.3, 5.242, -8.2, 0.7, 1.0},
         {0.153, 59.15, 0.675, 56.54, 0.7, 0.75},
         {0.675, 56.54, 0.945, 60.72, 0.75, 0.75},
         {0.945, 60.72, 1.43, 60.24, 0.75, 0.6},
         {1.43, 60.24, 1.907, 63.67, 0.6, 0.5},
         {20.69, 45.28, 20.37, 40.26, 1.0, 0.7},
         {20.37, 40.26, 19.512, 27.96, 0.7, 0.6},
         {19.75, 45.13, 20.37, 40.26, 0.6, 0.7},
         {20.37, 40.26, 20.77, 33.97, 0.7, 0.65},
         {18.616, 38.78, 18.746, 37.61, 1.0, 0.45},
         {18.746, 37.61, 18.908, 36.9, 0.45, 0.45},
         {18.908, 36.9, 18.982, 32.69, 0.45, 0.55},
         {18.982, 32.69, 18.835, 33.36, 0.55, 0.5},
         {18.835, 33.36, 18.746, 37.61, 0.5, 0.45},
         {12.443, -63.1, 12.519, -57.11, 0.9, 0.8},
         {12.795, -59.69, 12.252, -58.75, 0.85, 0.6}
      };
      float[] data = new float[segments.length * 8];
      Vector3f v = new Vector3f();

      for (int i = 0; i < segments.length; i++) {
         double[] s = segments[i];
         celestial(s[0], s[1], v);
         data[i * 8] = v.x;
         data[i * 8 + 1] = v.y;
         data[i * 8 + 2] = v.z;
         data[i * 8 + 3] = (float)s[4];
         celestial(s[2], s[3], v);
         data[i * 8 + 4] = v.x;
         data[i * 8 + 5] = v.y;
         data[i * 8 + 6] = v.z;
         data[i * 8 + 7] = (float)s[5];
      }

      return data;
   }

   private static void celestial(double raHours, double decDegrees, Vector3f out) {
      double ra = Math.toRadians(raHours * 15.0);
      double dec = Math.toRadians(decDegrees);
      out.set((float)Math.sin(dec), (float)(Math.cos(dec) * Math.cos(ra)), (float)(Math.cos(dec) * Math.sin(ra)));
      GALAXY.transformDirection(out).normalize();
   }

   private static final class Meteor {
      final Vector3f start = new Vector3f();
      final Vector3f end = new Vector3f();
      float age;
      float duration;
      float linger;
      float width;
      float brightness;
      float trail;
      float r;
      float g;
      float b;
      boolean fireball;
   }

   public static enum Palette {
      Specter("Specter", 10367999, 2059263),
      Amethyst("Amethyst", 12599551, 16732088),
      Ice("Ice", 5934335, 4251903),
      Ember("Ember", 16727406, 16751165),
      Aurora("Aurora", 3664032, 9063935),
      Custom("Custom", 0, 0);

      private final String title;
      final int first;
      final int second;

      private Palette(String title, int first, int second) {
         this.title = title;
         this.first = first;
         this.second = second;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Quality {
      Low("Low", 0),
      Medium("Medium", 1),
      High("High", 2),
      Ultra("Ultra", 3);

      private final String title;
      final int level;

      private Quality(String title, int level) {
         this.title = title;
         this.level = level;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
