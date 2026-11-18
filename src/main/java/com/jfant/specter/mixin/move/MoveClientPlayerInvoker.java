package com.jfant.specter.mixin.move;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({LocalPlayer.class})
public interface MoveClientPlayerInvoker {
   @Invoker("canStartSprinting")
   boolean specter$canStartSprinting();
}
