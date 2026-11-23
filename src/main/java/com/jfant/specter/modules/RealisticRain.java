package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.fx.FxParams;
import com.jfant.specter.render.GpuKit;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.io.InputStream;
import java.util.Optional;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3fc;

/**
 * Realistic rain: whiter, brighter rain streaks; the ground gets wet while it rains - darker, with puddles that mirror
 * the sky and everything around them, and little ripple rings where the drops land - and dries slowly after the rain
 * stops (the thin wet film first, the puddles last).
 *
 * <p>The wet ground is a screen-space pass in {@link com.jfant.specter.fx.WorldFx} (shader specter_wet). Only surfaces
 * rain can actually reach are wet (see {@link com.jfant.specter.fx.WetMap}): not under roofs or trees, not in deserts.
 * Reflections are of what is on screen, so in first person your own body is not in them (the game does not draw it);
 * in third person it is.
 */
public class RealisticRain extends Module {
   private static final Identifier RAIN = Identifier.withDefaultNamespace("textures/environment/rain.png");
   private static RealisticRain instance;
   private static boolean on;

   private final SettingGroup sgRain = this.settings.createGroup("Rain");
   private final SettingGroup sgGround = this.settings.createGroup("Wet Ground");

   private final Setting<Boolean> whiteRain = this.sgRain
      .add(new BoolSetting.Builder().name("white-rain").description("Rain streaks are white and lit, like real rain catching the light, instead of blue-grey.").defaultValue(true).build());
   private final Setting<Double> rainOpacity = this.sgRain
      .add(
         new DoubleSetting.Builder()
            .name("rain-opacity")
            .description("How solid the white streaks are. 1 = as vanilla.")
            .defaultValue(1.2)
            .range(0.3, 2.0)
            .sliderRange(0.3, 2.0)
            .visible(this.whiteRain::get)
            .build()
      );
   private final Setting<Double> rainAmount = this.sgRain
      .add(
         new DoubleSetting.Builder()
            .name("rain-amount")
            .description("How much rain you see: fewer streaks and fewer splashes. 1 = as vanilla.")
            .defaultValue(0.45)
            .range(0.05, 1.0)
            .sliderRange(0.05, 1.0)
            .build()
      );
   private final Setting<RealisticRain.Splashes> splashes = this.sgRain
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("splashes"))
                  .description("The little drops that bounce up where rain hits the ground. White matches the white rain; Off leaves only the ripples."))
               .defaultValue(RealisticRain.Splashes.White))
            .build()
      );
   private final Setting<Boolean> wetGround = this.sgGround
      .add(new BoolSetting.Builder().name("wet-ground").description("The ground gets wet, reflective and rippled while it rains, and dries slowly after.").defaultValue(true).build());
   private final Setting<Double> reflections = this.sgGround
      .add(
         new DoubleSetting.Builder()
            .name("reflections")
            .description("How strongly wet ground and puddles mirror things. 0 = no reflections (fastest).")
            .defaultValue(0.25)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Double> puddles = this.sgGround
      .add(
         new DoubleSetting.Builder()
            .name("puddles")
            .description("How much of the ground pools into mirror-like puddles.")
            .defaultValue(0.75)
            .range(0.0, 1.0)
            .sliderRange(0.0, 1.0)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Boolean> ripples = this.sgGround
      .add(
         new BoolSetting.Builder()
            .name("ripples")
            .description("Little rings spread where raindrops land, on puddles and on water.")
            .defaultValue(true)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Double> darken = this.sgGround
      .add(
         new DoubleSetting.Builder()
            .name("darken")
            .description("How much darker wet ground looks.")
            .defaultValue(0.4)
            .range(0.0, 0.7)
            .sliderRange(0.0, 0.7)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Double> wetTime = this.sgGround
      .add(
         new DoubleSetting.Builder()
            .name("soak-time")
            .description("Seconds of rain until the ground is fully wet.")
            .defaultValue(25.0)
            .range(1.0, 300.0)
            .sliderRange(5.0, 120.0)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Double> dryTime = this.sgGround
      .add(
         new DoubleSetting.Builder()
            .name("dry-time")
            .description("Seconds after the rain stops until the ground is dry again. The thin wet film goes first, puddles last.")
            .defaultValue(150.0)
            .range(5.0, 900.0)
            .sliderRange(10.0, 600.0)
            .visible(this.wetGround::get)
            .build()
      );
   private final Setting<Boolean> preview = this.sgGround
      .add(
         new BoolSetting.Builder()
            .name("preview")
            .description("Make the ground wet now, without waiting for rain (to try the look). Ripples need real rain.")
            .defaultValue(false)
            .visible(this.wetGround::get)
            .build()
      );

   private double wetness;
   private double presence;
   private long lastNanos;
   private ClientLevel level;
   private GpuTexture whiteTexture;
   private GpuTextureView whiteView;
   private double whiteBuiltFor = -1.0;

   public RealisticRain() {
      super(
         SpecterClientAddon.VISUALS,
         "realistic-rain",
         "Whiter rain, and the ground gets wet: darker, puddles that reflect the world, ripples where drops land. It dries slowly after. Only you see it."
      );
      instance = this;
   }

   public static boolean on() {
      return on;
   }

   @Override
   public void onActivate() {
      on = true;
      this.lastNanos = System.nanoTime();
   }

   @Override
   public void onDeactivate() {
      // The wet pass fades out over a second (see fill); 'on' drops when it reaches zero.
   }

   // ---------------------------------------------------------------- rain streaks

   /**
    * The whitened rain texture for the weather renderer, or null to draw vanilla rain. Called from inside the weather
    * render pass, so it must never create or upload anything - that happens on the tick (see onTick).
    */
   public static GpuTextureView whiteRainView() {
      RealisticRain m = instance;
      return m != null && m.isActive() && m.whiteRain.get() && m.whiteBuiltFor == m.rainOpacity.get() ? m.whiteView : null;
   }

   /** Builds (or rebuilds) the white rain texture between frames - uploading is not allowed while a pass is open. */
   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.whiteRain.get()) {
         double opacity = this.rainOpacity.get();
         if (this.whiteView == null || this.whiteBuiltFor != opacity) {
            this.buildWhite(opacity);
         }
      }
   }

   /** Rain columns lit brighter (as snow is), so the white reads at night and in shade too. */
   public static boolean brightRain() {
      RealisticRain m = instance;
      return m != null && m.isActive() && m.whiteRain.get();
   }

   private void buildWhite(double opacity) {
      this.releaseWhite();
      this.whiteBuiltFor = opacity;
      Optional<Resource> resource = this.mc.getResourceManager().getResource(RAIN);
      if (resource.isEmpty()) {
         return;
      }

      try (InputStream in = resource.get().open(); NativeImage src = NativeImage.read(in)) {
         try (NativeImage white = new NativeImage(src.getWidth(), src.getHeight(), false)) {
            for (int y = 0; y < src.getHeight(); y++) {
               for (int x = 0; x < src.getWidth(); x++) {
                  white.setPixel(x, y, whiten(src.getPixel(x, y), opacity));
               }
            }

            this.whiteTexture = GpuKit.upload("Specter white rain", white);
            this.whiteView = RenderSystem.getDevice().createTextureView(this.whiteTexture);
         }
      } catch (Exception e) {
         SpecterClientAddon.LOG.warn("Realistic Rain: could not build the white rain texture", e);
         this.releaseWhite();
      }
   }

   /**
    * One rain texel made white: the streak keeps its shape (alpha) and a little of its shading (so it is not a flat
    * stripe), but the blue is gone and it is scaled by the opacity setting. Pixels are ARGB (NativeImage#getPixel/setPixel on 26.x).
    */
   static int whiten(int argb, double opacity) {
      int a = argb >>> 24;
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      int luma = (r * 54 + g * 183 + b * 19) >> 8;
      int v = 215 + luma * 40 / 255;
      int na = (int)Math.min(255.0, a * opacity);
      // Neutral white with the faintest cool cast.
      return na << 24 | (v & 0xFF) << 16 | (v & 0xFF) << 8 | Math.min(255, v + 6);
   }

   private void releaseWhite() {
      if (this.whiteView != null) {
         this.whiteView.close();
      }

      if (this.whiteTexture != null) {
         this.whiteTexture.close();
      }

      this.whiteView = null;
      this.whiteTexture = null;
   }


   // ---------------------------------------------------------------- less rain, white splashes

   public static enum Splashes {
      White("White"),
      Off("Off"),
      Vanilla("Vanilla");

      private final String title;

      private Splashes(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   /** True while ClientLevel spawns a weather splash we want white (read by the water-drop particle mixin). */
   public static boolean spawningWhiteSplash;
   private static final Identifier[] WHITE_DROPS = {
      Identifier.withDefaultNamespace("generic_0"), Identifier.withDefaultNamespace("generic_1"), Identifier.withDefaultNamespace("generic_2")
   };
   private static int nextDrop;
   // AtlasIds lives in the data-generator package, so spell its value out.
   private static final Identifier PARTICLE_ATLAS = Identifier.withDefaultNamespace("particles");

   private static RealisticRain active() {
      RealisticRain m = instance;
      return m != null && m.isActive() ? m : null;
   }

   /** Whether this rain column is drawn: a fixed share of columns, picked by position so nothing flickers. */
   public static boolean keepRainColumn(int x, int z) {
      RealisticRain m = active();
      return m == null || keepColumn(x, z, m.rainAmount.get());
   }

   static boolean keepColumn(int x, int z, double amount) {
      if (amount >= 0.999) {
         return true;
      }

      int h = x * 73856093 ^ z * 19349663;
      h ^= h >>> 13;
      h *= 0x5bd1e995;
      h ^= h >>> 15;
      return (h & 0x3FF) < amount * 1024.0;
   }

   /** 0 = vanilla splash, 1 = white splash, -1 = no splash this time (fewer splashes with less rain, or Off). */
   public static int splashMode(float roll) {
      RealisticRain m = active();
      if (m == null) {
         return 0;
      }

      if (m.splashes.get() == RealisticRain.Splashes.Off || roll >= m.rainAmount.get()) {
         return -1;
      }

      return m.splashes.get() == RealisticRain.Splashes.White ? 1 : 0;
   }

   /** A small white droplet sprite from the particle atlas, or null if the atlas does not have one. */
   public static TextureAtlasSprite whiteDropSprite() {
      try {
         TextureAtlas atlas = instance.mc.getAtlasManager().getAtlasOrThrow(PARTICLE_ATLAS);
         Identifier id = WHITE_DROPS[nextDrop = (nextDrop + 1) % WHITE_DROPS.length];
         TextureAtlasSprite sprite = atlas.getSprite(id);
         return sprite != null && sprite.contents().name().equals(id) ? sprite : null;
      } catch (RuntimeException e) {
         return null;
      }
   }
   // ---------------------------------------------------------------- wet ground

   /** Called by WorldFx once a frame while the module is on (or fading out). */
   public static void fill(FxParams p) {
      if (instance != null && on) {
         instance.fillParams(p);
      }
   }

   private void fillParams(FxParams p) {
      long now = System.nanoTime();
      double dt = Math.min(0.25, Math.max(0.0, (now - this.lastNanos) / 1.0E9));
      this.lastNanos = now;
      ClientLevel world = this.mc.level;
      if (world != this.level) {
         // New world: start dry (or as wet as it is raining, so joining in a storm is not bone dry).
         this.level = world;
         this.wetness = world == null ? 0.0 : world.getRainLevel(1.0F);
      }

      boolean active = this.isActive() && this.wetGround.get();
      this.presence = approach(this.presence, active ? 1.0 : 0.0, dt);
      if (this.presence <= 0.0005 && !this.isActive()) {
         on = false;
         this.releaseWhite();
         return;
      }

      double target = this.preview.get() ? 1.0 : p.rain;
      this.wetness = stepWetness(this.wetness, target, dt, this.wetTime.get(), this.dryTime.get());
      float shown = (float)(this.wetness * this.presence);
      if (!p.overworld || shown <= 0.002F || p.underwater) {
         return;
      }

      p.wet = true;
      p.wetness = shown;
      p.wetReflect = this.reflections.get().floatValue();
      p.wetRipples = this.ripples.get() ? p.rain : 0.0F;
      p.wetPuddles = this.puddles.get().floatValue();
      p.wetDarken = this.darken.get().floatValue();
      p.wetRain = p.rain;
      Camera camera = this.mc.gameRenderer.mainCamera();
      Vector3fc sky = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1.0F);
      p.wetSky[0] = sky.x();
      p.wetSky[1] = sky.y();
      p.wetSky[2] = sky.z();
   }

   /**
    * Wetness moves toward the rain level: up in {@code soakSeconds} at full rain, down in {@code drySeconds}. Pure, so
    * it is unit tested.
    */
   static double stepWetness(double wet, double rain, double dt, double soakSeconds, double drySeconds) {
      rain = Math.max(0.0, Math.min(1.0, rain));
      if (rain > wet) {
         return Math.min(rain, wet + dt / Math.max(0.1, soakSeconds));
      } else {
         return Math.max(rain, wet - dt / Math.max(0.1, drySeconds));
      }
   }

   private static double approach(double v, double target, double dt) {
      return v < target ? Math.min(target, v + dt) : Math.max(target, v - dt);
   }
}
