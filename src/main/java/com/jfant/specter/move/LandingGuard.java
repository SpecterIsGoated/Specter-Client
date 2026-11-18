package com.jfant.specter.move;

import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class LandingGuard {
   private static final int MAX_TICKS = 600;
   private static volatile boolean on;
   private static int ticks;
   private static final Minecraft mc = Minecraft.getInstance();

   LandingGuard() {
   }

   public static void start() {
      on = true;
      ticks = 0;
   }

   public static void stop() {
      on = false;
   }

   public static boolean active() {
      return on;
   }

   @EventHandler
   private void onSend(PacketEvent.Send event) {
      if (on) {
         GroundSpoof.apply(event.packet);
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (on) {
         LocalPlayer player = mc.player;
         if (player == null || player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || ++ticks > 600) {
            on = false;
         }
      }
   }

   @EventHandler
   private void onLeft(GameLeftEvent event) {
      on = false;
   }
}
