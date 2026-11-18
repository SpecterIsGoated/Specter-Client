package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.camera.GhostView;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;

public class GhostFreelook extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> seeThroughWalls = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("see-through-walls")
            .description("Let the camera pass through blocks instead of pulling in against them.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> keepMining = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-mining")
            .description(
               "If you were holding attack when it came on, your body keeps mining - and keeps walking if you were walking too - after you let go. Walking without mining stops."
            )
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> keepSneaking = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-sneaking")
            .description("If you were sneaking when it came on, your body keeps sneaking.")
            .defaultValue(true)
            .build()
      );
   public float yaw;
   public float pitch;
   private final GhostView.KeyLatch latch = new GhostView.KeyLatch();
   private CameraType savedPerspective;
   private boolean live;
   private boolean handingOff;
   private boolean suppressNextActivation;

   public GhostFreelook() {
      super(SpecterClientAddon.TOOLS, "ghost-freelook", "Look around without turning: your body keeps facing and walking the same way.");
      GhostView.init();
   }

   public static GhostFreelook on() {
      Modules modules = Modules.get();
      GhostFreelook module = modules == null ? null : modules.get(GhostFreelook.class);
      return module != null && module.isActive() && module.live ? module : null;
   }

   public boolean seeThroughWalls() {
      return this.seeThroughWalls.get();
   }

   @Override
   public void onActivate() {
      if (this.suppressNextActivation) {
         this.suppressNextActivation = false;
         this.toggle();
      } else if (this.mc.player != null && this.mc.level != null && this.mc.options != null) {
         this.live = true;
         GhostView.enter();
         GhostFreecam cam = GhostFreecam.on();
         if (cam != null) {
            this.yaw = cam.yaw;
            this.pitch = cam.pitch;
            this.savedPerspective = cam.handOff(this.latch);
         } else {
            this.yaw = this.mc.player.getYRot();
            this.pitch = this.mc.player.getXRot();
            this.savedPerspective = this.mc.options.getCameraType();
            this.latch.capture(this.keepMining.get(), this.keepSneaking.get());
         }

         this.mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      } else {
         this.toggle();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.live) {
         this.live = false;
         if (this.handingOff) {
            this.handingOff = false;
         } else {
            this.mc.options.setCameraType(this.savedPerspective != null ? this.savedPerspective : CameraType.FIRST_PERSON);
            this.latch.release();
            KeyMapping.setAll();
         }

         GhostView.exit();
      }
   }

   CameraType handOff(GhostView.KeyLatch into) {
      into.copyFrom(this.latch);
      this.handingOff = true;
      if (this.toggleOnBindRelease && this.keybind.isPressed()) {
         this.suppressNextActivation = true;
      }

      CameraType original = this.savedPerspective;
      this.toggle();
      return original;
   }

   public void turn(double deltaYaw, double deltaPitch) {
      this.yaw = Mth.wrapDegrees(this.yaw + (float)deltaYaw);
      this.pitch = Mth.clamp(this.pitch + (float)deltaPitch, -90.0F, 90.0F);
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.toggle();
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player != null) {
         this.latch.apply();
      }
   }
}
