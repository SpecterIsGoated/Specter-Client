package com.jfant.specter.move;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.mixin.move.MoveClientPlayerInvoker;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;

public class AutoSprint extends Module {
   private static AutoSprint instance;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<AutoSprint.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description(
                     "Forward: sprint while you walk forward, like holding the sprint key. All Directions: also while walking sideways or backwards (vanilla never does; some anti-cheats check for it)."
                  ))
               .defaultValue(AutoSprint.Mode.Forward))
            .build()
      );
   private final Setting<Boolean> keepAfterHit = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-after-hit")
            .description(
               "Go straight back to sprinting after you hit something (the game stops your sprint on a hit). Off: no auto-sprint after a hit until you let go of the walking keys, so you can time your own sprint-reset hits."
            )
            .defaultValue(true)
            .build()
      );
   private boolean pausedByHit;

   public AutoSprint() {
      super(
         SpecterClientAddon.TOOLS,
         "auto-sprint",
         "Sprints for you whenever you move, forward or (optionally) any direction. Obeys the game's own limits: hunger, blindness, slowing items, walls. Forward sprint is what holding the key does; sprinting sideways or backwards is not vanilla and can be flagged."
      );
      instance = this;
   }

   @Override
   public void onActivate() {
      this.pausedByHit = false;
   }

   public static boolean omniMoving() {
      AutoSprint sprint = instance;
      return sprint != null && sprint.isActive() && sprint.mode.get() == AutoSprint.Mode.Omni && MoveInput.anyHorizontal();
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (!this.keepAfterHit.get()) {
         this.pausedByHit = true;
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      LocalPlayer player = this.mc.player;
      if (player != null && this.mc.screen == null) {
         boolean moving = this.mode.get() == AutoSprint.Mode.Omni ? MoveInput.anyHorizontal() : MoveInput.forward() && !MoveInput.back();
         if (!MoveInput.anyHorizontal()) {
            this.pausedByHit = false;
         }

         if (moving && !this.pausedByHit && !player.isSprinting()) {
            if (((MoveClientPlayerInvoker)player).specter$canStartSprinting()) {
               player.setSprinting(true);
            }
         }
      }
   }

   @Override
   public String getInfoString() {
      return this.mode.get().toString();
   }

   public static enum Mode {
      Forward("Forward"),
      Omni("All Directions");

      private final String title;

      private Mode(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }
}
