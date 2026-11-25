package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.sky.SkyboxRenderer;

/**
 * A photo sky all the way round: a 360 degree panorama drawn behind the world instead of the sky. DonutTravel picks
 * one for each destination; it can also be chosen here. Only you see it.
 */
public class Skyboxes extends Module {
   private static Skyboxes instance;
   private static boolean on;

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Skyboxes.Sky> sky = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("sky"))
                  .description("Which sky surrounds you."))
               .defaultValue(Skyboxes.Sky.Space))
            .build()
      );
   private final Setting<Integer> rotation = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("rotation")
            .description("Turns the sky round, in degrees.")
            .defaultValue(0)
            .range(0, 359)
            .sliderRange(0, 359)
            .build()
      );
   private final Setting<Double> brightness = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("brightness")
            .description("How bright the sky is drawn.")
            .defaultValue(1.0)
            .range(0.2, 2.0)
            .sliderRange(0.2, 2.0)
            .build()
      );

   public Skyboxes() {
      super(SpecterClientAddon.VISUALS, "skybox", "A 360 degree photo sky around you: tropical beach, Tokyo cherry blossoms, Saturn in space and more. Only you see it.");
      instance = this;
   }

   @Override
   public void onActivate() {
      on = true;
   }

   @Override
   public void onDeactivate() {
      on = false;
   }

   /** The sky to draw this frame, or null. */
   public static Skyboxes.Sky current() {
      return on && instance != null ? instance.sky.get() : null;
   }

   public static float yawRadians() {
      Skyboxes.Sky s = current();
      return s == null ? 0.0F : (float)Math.toRadians(instance.rotation.get() + s.yaw);
   }

   public static float brightness() {
      return instance == null ? 1.0F : instance.brightness.get().floatValue();
   }

   /** Whether the panorama replaces the sky right now (overworld-style sky, picture loaded). */
   public static boolean active() {
      return current() != null && SkyboxRenderer.ready();
   }

   public static enum Sky {
      Tropical("Tropical Beach", "tropical", 0),
      Tokyo("Tokyo Cherry Blossoms", "tokyo", 0),
      Cyberpunk("Neon City", "cyberpunk", 0),
      Sunset("Sunset Beach", "sunset", 0),
      Arctic("Arctic Snow", "arctic", 0),
      Space("Saturn", "space", 0),
      Desert("Desert", "desert", 0),
      Forest("Forest", "forest", 0);

      private final String title;
      public final String file;
      final int yaw;

      private Sky(String title, String file, int yaw) {
         this.title = title;
         this.file = file;
         this.yaw = yaw;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
