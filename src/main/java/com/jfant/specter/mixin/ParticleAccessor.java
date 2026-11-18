package com.jfant.specter.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Particle.class})
public interface ParticleAccessor {
   @Accessor("age")
   int specter$getAge();

   @Accessor("lifetime")
   int specter$getMaxAge();
}
