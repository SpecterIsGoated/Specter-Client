package com.jfant.specter.core.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerboundMovePlayerPacket.class})
public interface PlayerMoveC2SPacketAccessor {
   @Mutable
   @Accessor("y")
   void meteor$setY(double var1);

   @Mutable
   @Accessor("onGround")
   void meteor$setOnGround(boolean var1);
}
