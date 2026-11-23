package com.jfant.specter.phone.apps.travel;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side ambience around the player for a destination: petals, fireflies, neon motes, blowing sand, star dust.
 * Only you see them; a handful per tick, fewer with the Particles option on Decreased, none on Minimal.
 */
public final class AmbientParticles {
   public enum Kind {
      PETALS,
      FIREFLIES,
      NEON,
      SAND,
      STARDUST,
      POLLEN
   }

   private static final Random RANDOM = new Random();
   private static final int[] NEON_COLORS = {0x00F0FF, 0xFF2BD6, 0x8B5CF6, 0x22FF88};

   private AmbientParticles() {
   }

   public static void tick(Kind kind) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.isPaused()) return;
      int setting = mc.options.particles().get().ordinal();
      if (setting >= 2) return;
      int count = switch (kind) {
         case PETALS -> 3;
         case FIREFLIES -> 2;
         case NEON -> 3;
         case SAND -> 6;
         case STARDUST -> 2;
         case POLLEN -> 2;
      };
      if (setting == 1) count = Math.max(1, count / 2);
      Vec3 at = mc.player.position();
      for (int i = 0; i < count; i++) {
         double x = at.x + (RANDOM.nextDouble() - 0.5) * 28, z = at.z + (RANDOM.nextDouble() - 0.5) * 28;
         double y = at.y + RANDOM.nextDouble() * 10 - 1;
         ParticleOptions p;
         double vx = 0, vy = 0, vz = 0;
         switch (kind) {
            case PETALS -> {
               p = ParticleTypes.CHERRY_LEAVES;
               y = at.y + 6 + RANDOM.nextDouble() * 6;
            }
            case FIREFLIES -> {
               p = ParticleTypes.FIREFLY;
               y = at.y + RANDOM.nextDouble() * 4;
            }
            case NEON -> {
               p = new DustParticleOptions(NEON_COLORS[RANDOM.nextInt(NEON_COLORS.length)], 0.9F);
               vy = 0.02;
            }
            case SAND -> {
               p = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
               y = at.y + RANDOM.nextDouble() * 3;
            }
            case STARDUST -> {
               p = ParticleTypes.END_ROD;
               vx = (RANDOM.nextDouble() - 0.5) * 0.01;
               vy = 0.005;
               vz = (RANDOM.nextDouble() - 0.5) * 0.01;
            }
            default -> p = ParticleTypes.SPORE_BLOSSOM_AIR;
         }
         mc.level.addParticle(p, x, y, z, vx, vy, vz);
      }
   }
}
