package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Spec;
import com.jfant.specter.anim.Tween;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.festive.Snowfield;
import java.util.Random;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public class Snowfall extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWorld = this.settings.createGroup("In The World");
   private final SettingGroup sgScreen = this.settings.createGroup("Across The Screen");
   private final Setting<Snowfall.Where> where = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("where"))
                  .description("Snow in the world around you, drifting across your screen, or both."))
               .defaultValue(Snowfall.Where.Both))
            .build()
      );
   private final Setting<Double> density = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("density")
            .description("How much snow there is: a few flakes up to a blizzard.")
            .defaultValue(1.0)
            .range(0.1, 4.0)
            .sliderRange(0.1, 4.0)
            .build()
      );
   private final Setting<Double> intensity = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("intensity")
            .description("How fast it comes down: a gentle fall up to driving snow.")
            .defaultValue(1.0)
            .range(0.2, 3.0)
            .sliderRange(0.2, 3.0)
            .build()
      );
   private final Setting<Double> wind = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("wind")
            .description("A steady wind blowing the snow sideways. 0 lets it fall straight; gusts come and go either way.")
            .defaultValue(0.2)
            .range(-1.0, 1.0)
            .sliderRange(-1.0, 1.0)
            .build()
      );
   private final Setting<Snowfall.Flakes> flakes = this.sgWorld
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("flakes"))
                     .description("Snowflakes are big and showy; fine powder is small and drifts longer; mixed is both."))
                  .defaultValue(Snowfall.Flakes.Mixed))
               .visible(() -> this.where.get() != Snowfall.Where.Screen))
            .build()
      );
   private final Setting<Integer> radius = this.sgWorld
      .add(
         new IntSetting.Builder()
            .name("radius")
            .description("How far around you snow falls, in blocks.")
            .defaultValue(14)
            .range(6, 32)
            .sliderRange(6, 32)
            .visible(() -> this.where.get() != Snowfall.Where.Screen)
            .build()
      );
   private final Setting<Boolean> openSkyOnly = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("open-sky-only")
            .description("Snow only falls where there is nothing overhead, so it stays out of caves and houses.")
            .defaultValue(true)
            .visible(() -> this.where.get() != Snowfall.Where.Screen)
            .build()
      );
   private final Setting<Double> screenAmount = this.sgScreen
      .add(
         new DoubleSetting.Builder()
            .name("screen-amount")
            .description("How much of the snow across the screen there is, next to the density above.")
            .defaultValue(0.6)
            .range(0.1, 2.0)
            .sliderRange(0.1, 2.0)
            .visible(() -> this.where.get() != Snowfall.Where.World)
            .build()
      );
   private final Setting<Double> flakeSize = this.sgScreen
      .add(
         new DoubleSetting.Builder()
            .name("flake-size")
            .description("How big the flakes across the screen are.")
            .defaultValue(1.0)
            .range(0.5, 3.0)
            .sliderRange(0.5, 3.0)
            .visible(() -> this.where.get() != Snowfall.Where.World)
            .build()
      );
   private final Setting<Double> screenOpacity = this.sgScreen
      .add(
         new DoubleSetting.Builder()
            .name("opacity")
            .description("How visible the flakes across the screen are.")
            .defaultValue(0.7)
            .range(0.1, 1.0)
            .sliderRange(0.1, 1.0)
            .visible(() -> this.where.get() != Snowfall.Where.World)
            .build()
      );
   private final Setting<Boolean> hideInMenus = this.sgScreen
      .add(
         new BoolSetting.Builder()
            .name("hide-in-menus")
            .description("No snow across the screen while an inventory or menu is open.")
            .defaultValue(true)
            .visible(() -> this.where.get() != Snowfall.Where.World)
            .build()
      );
   private final Snowfield screen = new Snowfield();
   private final Random random = new Random();
   private final Tween shown = Tween.progress(Spec.WORLD_FADE, false);
   private double owed;
   private double gust;

   public Snowfall() {
      super(
         SpecterClientAddon.VISUALS,
         "snowfall",
         "Snow: real flakes falling in the world around you, flakes drifting across your screen, or both. Only you see it."
      );
   }

   @Override
   public void onActivate() {
      this.shown.set(0.0);
      this.owed = 0.0;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.where.get() != Snowfall.Where.Screen && this.mc.level != null && this.mc.player != null && !this.mc.isPaused()) {
         Vec3 eye = this.mc.gameRenderer.mainCamera().position();
         int r = this.radius.get();
         this.owed = this.owed + this.density.get() * r * r * 0.012;
         this.gust += 0.011;
         double blow = this.wind.get() * 0.09 + Math.sin(this.gust) * 0.02 + Math.sin(this.gust * 0.37 + 1.1) * 0.015;
         double fall = this.intensity.get();
         Snowfall.Flakes kind = this.flakes.get();
         int budget = Math.min((int)this.owed, 220);
         this.owed -= budget;

         for (int i = 0; i < budget; i++) {
            double angle = this.random.nextDouble() * 3.141592653589793 * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * r;
            double x = eye.x + Math.cos(angle) * dist;
            double z = eye.z + Math.sin(angle) * dist;
            double y = eye.y - 1.0 + this.random.nextDouble() * 9.0;
            if (this.openSkyOnly.get()) {
               int roof = this.mc.level.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(x), (int)Math.floor(z));
               if (y < roof) {
                  continue;
               }
            }

            boolean powder = kind == Snowfall.Flakes.Powder || kind == Snowfall.Flakes.Mixed && this.random.nextInt(3) == 0;
            double vx = blow + (this.random.nextDouble() - 0.5) * 0.02;
            double vz = blow * 0.45 + (this.random.nextDouble() - 0.5) * 0.02;
            Particle particle = this.mc
               .particleEngine
               .createParticle(powder ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE, x, y, z, vx, -0.04 * fall, vz);
            if (particle != null) {
               if (!powder) {
                  particle.setLifetime((int)((70 + this.random.nextInt(70)) / Math.max(0.5, fall)));
                  particle.scale(0.8F + this.random.nextFloat() * 0.9F);
               }

               particle.setParticleSpeed(vx, -0.04 * fall, vz);
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render2DEvent event) {
      if (this.where.get() != Snowfall.Where.World) {
         boolean visible = !this.hideInMenus.get() || this.mc.gui.screen() == null;
         float alpha = this.shown.to(visible ? 1.0 : 0.0, Spec.WORLD_FADE.scaled(visible ? 1.0 : 0.35)).getF() * this.screenOpacity.get().floatValue();
         if (!(alpha <= 0.01F)) {
            GuiGraphicsExtractor ctx = event.graphics;
            this.screen
               .draw(
                  ctx,
                  ctx.guiWidth(),
                  ctx.guiHeight(),
                  (float)(this.density.get() * this.screenAmount.get()),
                  this.intensity.get().floatValue(),
                  this.wind.get().floatValue(),
                  this.flakeSize.get().floatValue(),
                  alpha
               );
         }
      }
   }

   public static enum Flakes {
      Flakes("Snowflakes"),
      Powder("Fine powder"),
      Mixed("Mixed");

      private final String title;

      private Flakes(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   public static enum Where {
      World("In the world"),
      Screen("Across the screen"),
      Both("Both");

      private final String title;

      private Where(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
