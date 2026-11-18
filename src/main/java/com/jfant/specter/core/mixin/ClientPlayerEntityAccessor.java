package com.jfant.specter.core.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LocalPlayer.class})
public interface ClientPlayerEntityAccessor {
   @Accessor("positionReminder")
   void meteor$setTicksSinceLastPositionPacketSent(int var1);
}
