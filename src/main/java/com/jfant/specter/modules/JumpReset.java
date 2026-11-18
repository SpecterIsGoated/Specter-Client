package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.input.Input;
import com.jfant.specter.pvp.Pace;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

public class JumpReset extends Module {
   private static final double MIN_PUSH = 0.1;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Double> chance = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("chance")
            .description("Percent of knockbacks that get a jump reset.")
            .defaultValue(100.0)
            .range(0.0, 100.0)
            .sliderRange(0.0, 100.0)
            .build()
      );
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Say in chat (only you see it) each knockback, and whether it was jumped.")
            .defaultValue(false)
            .build()
      );
   private boolean pending;
   private boolean pressed;

   public JumpReset() {
      super(SpecterClientAddon.PVP, "jump-reset", "Jumps on the tick after a hit knocks you back, the way players jump-reset by hand, to take less knockback.");
   }

   @Override
   public void onDeactivate() {
      if (this.pressed) {
         this.restore();
      }

      this.pending = this.pressed = false;
   }

   @EventHandler
   private void onPacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundSetEntityMotionPacket packet) {
         Vec3 velocity = packet.getMovement();
         if (!(velocity.y <= 0.0) && !(Math.hypot(velocity.x, velocity.z) < 0.1)) {
            int id = packet.getId();
            this.mc.execute(() -> {
               if (this.mc.player != null && this.mc.player.getId() == id) {
                  this.pending = true;
               }
            });
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.pressed) {
         this.pressed = false;
         this.restore();
      }

      if (this.pending) {
         this.pending = false;
         LocalPlayer player = this.mc.player;
         if (player != null && this.mc.screen == null) {
            if (player.onGround()
               && player.isSprinting()
               && Input.isPressed(this.mc.options.keyUp)
               && !player.isInWater()
               && !player.isInLava()
               && !player.onClimbable()
               && !player.isPassenger()
               && !player.isFallFlying()) {
               if (!Pace.chance(this.chance.get())) {
                  if (this.debug.get()) {
                     this.info("Knockback, no jump: chance.", new Object[0]);
                  }
               } else {
                  this.mc.options.keyJump.setDown(true);
                  this.pressed = true;
                  if (this.debug.get()) {
                     this.info("Knockback: jumped.", new Object[0]);
                  }
               }
            } else {
               if (this.debug.get()) {
                  this.info("Knockback, no jump: not sprinting forward on the ground.", new Object[0]);
               }
            }
         }
      }
   }

   private void restore() {
      this.mc.options.keyJump.setDown(this.mc.screen == null && Input.isPressed(this.mc.options.keyJump));
   }
}
