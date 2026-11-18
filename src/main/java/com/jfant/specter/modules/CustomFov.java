package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.anim.Channel;
import com.jfant.specter.anim.Spring;
import com.jfant.specter.core.events.meteor.MouseScrollEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class CustomFov extends Module {
   private static CustomFov instance;
   private static volatile boolean on;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgSituations = this.settings.createGroup("Situations");
   private final SettingGroup sgMovement = this.settings.createGroup("Movement");
   private final SettingGroup sgZoom = this.settings.createGroup("Zoom");
   private final Setting<Integer> fov = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("fov")
            .description("Your field of view in degrees. The game's own slider stops at 110; this goes from a narrow 30 to a very wide 150.")
            .defaultValue(100)
            .range(30, 150)
            .sliderRange(30, 150)
            .build()
      );
   private final Setting<Double> transition = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("transition-time")
            .description("How long the view takes to glide to a new field of view, in seconds. 0 changes it at once.")
            .defaultValue(0.28)
            .range(0.0, 2.0)
            .sliderRange(0.0, 1.2)
            .decimalPlaces(2)
            .build()
      );
   private final Setting<Boolean> vanillaEffects = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-game-effects")
            .description("Keep the game's own FOV changes (speed and slowness effects, drawing a bow) on top of yours. Off, only the settings here change it.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> sprintOn = this.toggle(this.sgSituations, "sprinting", "Use a different field of view while sprinting.", true);
   private final Setting<Integer> sprintFov = this.value(this.sgSituations, "sprinting-fov", "Field of view while sprinting.", 108, this.sprintOn);
   private final Setting<Boolean> glideOn = this.toggle(
      this.sgSituations, "gliding", "Use a different field of view while flying with an elytra or in creative flight.", true
   );
   private final Setting<Integer> glideFov = this.value(this.sgSituations, "gliding-fov", "Field of view while gliding or flying.", 114, this.glideOn);
   private final Setting<Boolean> swimOn = this.toggle(this.sgSituations, "under-water", "Use a different field of view while your head is under water.", false);
   private final Setting<Integer> swimFov = this.value(this.sgSituations, "under-water-fov", "Field of view under water.", 92, this.swimOn);
   private final Setting<Boolean> aimOn = this.toggle(
      this.sgSituations, "aiming", "Use a different field of view while drawing a bow, charging a crossbow or trident.", true
   );
   private final Setting<Integer> aimFov = this.value(this.sgSituations, "aiming-fov", "Field of view while aiming.", 78, this.aimOn);
   private final Setting<Boolean> sneakOn = this.toggle(this.sgSituations, "sneaking", "Use a different field of view while sneaking.", false);
   private final Setting<Integer> sneakFov = this.value(this.sgSituations, "sneaking-fov", "Field of view while sneaking.", 94, this.sneakOn);
   private final Setting<Boolean> speedEffect = this.sgMovement
      .add(
         new BoolSetting.Builder()
            .name("speed-effect")
            .description("Open the view up a little as you move faster, on top of everything else: a sense of speed on ice, in boats, on horses and in flight.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> speedAmount = this.sgMovement
      .add(
         new DoubleSetting.Builder()
            .name("speed-amount")
            .description("How many degrees are added at full speed.")
            .defaultValue(8.0)
            .range(0.0, 30.0)
            .sliderRange(0.0, 30.0)
            .decimalPlaces(1)
            .visible(this.speedEffect::get)
            .build()
      );
   private final Setting<Double> speedFull = this.sgMovement
      .add(
         new DoubleSetting.Builder()
            .name("full-speed")
            .description("The speed, in blocks a second, at which the whole amount is reached. Sprinting is about 5.6, an elytra 30 and more.")
            .defaultValue(18.0)
            .range(4.0, 80.0)
            .sliderRange(4.0, 60.0)
            .decimalPlaces(1)
            .visible(this.speedEffect::get)
            .build()
      );
   private final Setting<Keybind> zoomKey = this.sgZoom
      .add(new KeybindSetting.Builder().name("zoom-key").description("Hold to zoom in. The view glides in and back out.").defaultValue(Keybind.none()).build());
   private final Setting<Double> zoomFactor = this.sgZoom
      .add(
         new DoubleSetting.Builder()
            .name("zoom")
            .description("How far the zoom key zooms in: 4 makes things four times bigger.")
            .defaultValue(4.0)
            .range(1.5, 30.0)
            .sliderRange(1.5, 20.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Boolean> zoomScroll = this.sgZoom
      .add(
         new BoolSetting.Builder()
            .name("scroll-to-zoom")
            .description("While the zoom key is held, the mouse wheel zooms further in and out instead of changing your hotbar slot.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> zoomTime = this.sgZoom
      .add(
         new DoubleSetting.Builder()
            .name("zoom-time")
            .description("How long zooming in and out takes, in seconds.")
            .defaultValue(0.18)
            .range(0.0, 1.0)
            .sliderRange(0.0, 0.6)
            .decimalPlaces(2)
            .build()
      );
   private final Spring shown = new Spring(0.28, Channel.CAMERA, 70.0);
   private final Spring zoom = new Spring(0.18, Channel.CAMERA, 1.0);
   private final Spring speedExtra = new Spring(0.6, Channel.CAMERA, 0.0);
   private double wheel = 1.0;
   private boolean primed;

   public CustomFov() {
      super(
         SpecterClientAddon.VISUALS,
         "custom-fov",
         "Your own field of view, with separate values for sprinting, gliding, swimming and aiming, a sense of speed, and a zoom key. Every change glides."
      );
      instance = this;
   }

   private Setting<Boolean> toggle(SettingGroup group, String name, String description, boolean def) {
      return group.add(new BoolSetting.Builder().name(name).description(description).defaultValue(def).build());
   }

   private Setting<Integer> value(SettingGroup group, String name, String description, int def, Setting<Boolean> when) {
      return group.add(
         new IntSetting.Builder().name(name).description(description).defaultValue(def).range(30, 150).sliderRange(30, 150).visible(when::get).build()
      );
   }

   @Override
   public void onActivate() {
      on = true;
      this.primed = false;
      this.wheel = 1.0;
   }

   @Override
   public void onDeactivate() {
      on = false;
   }

   @EventHandler
   private void onScroll(MouseScrollEvent event) {
      if (this.zoomScroll.get() && this.mc.gui.screen() == null && this.zoomKey.get().isPressed()) {
         this.wheel = Math.clamp(this.wheel * (event.value > 0.0 ? 1.25 : 0.8), 1.0 / this.zoomFactor.get(), 30.0 / this.zoomFactor.get());
         event.cancel();
      }
   }

   public static float apply(float vanilla) {
      return on && instance != null ? instance.compute(vanilla) : vanilla;
   }

   private float compute(float vanilla) {
      LocalPlayer player = this.mc.player;
      if (player == null) {
         return vanilla;
      } else {
         double target = this.fov.get().intValue();
         if (this.aimOn.get() && aiming(player)) {
            target = this.aimFov.get().intValue();
         } else if (!this.glideOn.get() || !player.isFallFlying() && !player.getAbilities().flying) {
            if (this.swimOn.get() && player.isUnderWater()) {
               target = this.swimFov.get().intValue();
            } else if (this.sneakOn.get() && player.isShiftKeyDown()) {
               target = this.sneakFov.get().intValue();
            } else if (this.sprintOn.get() && player.isSprinting()) {
               target = this.sprintFov.get().intValue();
            }
         } else {
            target = this.glideFov.get().intValue();
         }

         if (this.vanillaEffects.get()) {
            double base = this.mc.options.fov().get().intValue();
            if (base > 1.0) {
               target *= Math.clamp(vanilla / base, 0.5, 1.6);
            }
         }

         if (this.speedEffect.get()) {
            Vec3 v = player.getVehicle() != null ? player.getVehicle().getDeltaMovement() : player.getDeltaMovement();
            double perSecond = Math.sqrt(v.x * v.x + v.z * v.z + v.y * v.y * 0.25) * 20.0;
            double share = Math.clamp((perSecond - 4.5) / Math.max(1.0, this.speedFull.get() - 4.5), 0.0, 1.0);
            target += this.speedExtra.follow(share * this.speedAmount.get());
         } else {
            target += this.speedExtra.follow(0.0);
         }

         boolean zooming = this.mc.gui.screen() == null && this.zoomKey.get().isPressed();
         if (!zooming) {
            this.wheel = 1.0;
         }

         double zoomed = this.zoom.response(this.zoomTime.get()).follow(zooming ? this.zoomFactor.get() * this.wheel : 1.0);
         if (!this.primed) {
            this.primed = true;
            this.shown.set(vanilla);
         }

         double value = this.shown.response(this.transition.get()).follow(Math.clamp(target, 10.0, 170.0));
         double half = Math.toRadians(value) / 2.0;
         double out = Math.toDegrees(2.0 * Math.atan(Math.tan(half) / Math.max(1.0, zoomed)));
         return (float)Math.clamp(out, 1.0, 170.0);
      }
   }

   private static boolean aiming(LocalPlayer player) {
      if (!player.isUsingItem()) {
         return false;
      } else {
         Item item = player.getUseItem().getItem();
         return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT || item == Items.SPYGLASS;
      }
   }
}
