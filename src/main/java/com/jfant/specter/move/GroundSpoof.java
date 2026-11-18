package com.jfant.specter.move;

import com.jfant.specter.core.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class GroundSpoof {
   private GroundSpoof() {
   }

   public static boolean apply(Packet<?> packet) {
      if (packet instanceof ServerboundMovePlayerPacket move) {
         if (!move.isOnGround()) {
            ((ServerboundMovePlayerPacketAccessor)move).meteor$setOnGround(true);
         }

         return true;
      } else {
         return false;
      }
   }
}
