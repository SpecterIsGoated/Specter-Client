package com.jfant.specter.core.events.world;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.core.particles.ParticleOptions;

public class ParticleEvent extends Cancellable {
   private static final ParticleEvent INSTANCE = new ParticleEvent();
   public ParticleOptions particle;

   public static ParticleEvent get(ParticleOptions particle) {
      INSTANCE.setCancelled(false);
      INSTANCE.particle = particle;
      return INSTANCE;
   }
}
