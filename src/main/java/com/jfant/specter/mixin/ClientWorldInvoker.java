package com.jfant.specter.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ClientLevel.class})
public interface ClientWorldInvoker {
   @Invoker("getSkyFlashTime")
   int specter$lightningTicksLeft();
}
