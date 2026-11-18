package com.jfant.specter.core.events.world;

import com.jfant.specter.core.events.Cancellable;
import net.minecraft.client.resources.sounds.SoundInstance;

public class PlaySoundEvent extends Cancellable {
   private static final PlaySoundEvent INSTANCE = new PlaySoundEvent();
   public SoundInstance sound;

   public static PlaySoundEvent get(SoundInstance sound) {
      INSTANCE.setCancelled(false);
      INSTANCE.sound = sound;
      return INSTANCE;
   }
}
