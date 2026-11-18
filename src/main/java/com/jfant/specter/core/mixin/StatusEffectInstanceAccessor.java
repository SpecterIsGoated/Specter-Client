package com.jfant.specter.core.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MobEffectInstance.class})
public interface StatusEffectInstanceAccessor {
   @Accessor("duration")
   void meteor$setDuration(int var1);
}
