package com.jfant.specter.mech;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The mech's own sounds (synthesised, see tools/mech/sounds.js). The events are client side only and are not put in
 * the sound registry - the sound manager finds them by name in assets/specter/sounds.json, so nothing has to match
 * the server.
 */
public final class MechSounds {
   public static final SoundEvent TRANSFORM = event("mech.transform");
   public static final SoundEvent TRANSFORM_BACK = event("mech.transform_back");
   public static final SoundEvent IGNITE = event("mech.ignite");
   public static final SoundEvent AFTERBURNER = event("mech.afterburner");
   public static final SoundEvent ENGINE = event("mech.engine");
   public static final SoundEvent STEP = event("mech.step");
   public static final SoundEvent LAND = event("mech.land");
   private static final RandomSource RANDOM = RandomSource.create();

   private MechSounds() {
   }

   private static SoundEvent event(String name) {
      return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("specter", name));
   }

   public static void play(SoundEvent event, double x, double y, double z, float volume, float pitch) {
      if (volume <= 0.001F) return;
      Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch, RANDOM, x, y, z));
   }

   /** Looping jet engine that follows a mech; volume and pitch follow speed and afterburner. */
   public static final class Engine extends AbstractTickableSoundInstance {
      private final MechAnim anim;
      private final MechVolume volumeSource;
      private float level;

      /** Supplies the master volume (the module's sound volume, 0 when sounds are off). */
      public interface MechVolume {
         float volume();

         boolean alive(MechAnim anim);
      }

      public Engine(MechAnim anim, MechVolume volume) {
         super(ENGINE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
         this.anim = anim;
         this.volumeSource = volume;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = anim.renderX;
         this.y = anim.renderY;
         this.z = anim.renderZ;
      }

      @Override
      public void tick() {
         if (!this.volumeSource.alive(this.anim)) {
            this.stop();
            return;
         }
         float jet = MechPose.smooth((this.anim.transform() - 0.8F) / 0.2F);
         float speed = Math.min(1.0F, this.anim.speed() / 1.8F);
         float boost = this.anim.boost();
         float target = jet * (0.28F + 0.3F * speed + 0.45F * boost) * this.volumeSource.volume();
         this.level += (target - this.level) * 0.2F;
         if (this.level < 0.003F && jet <= 0) {
            this.stop();
            return;
         }
         this.volume = Math.max(0.001F, this.level);
         this.pitch = 0.75F + 0.35F * speed + 0.25F * boost;
         this.x = this.anim.renderX;
         this.y = this.anim.renderY;
         this.z = this.anim.renderZ;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
