package com.jfant.specter.pvp;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class Melee {
   private Melee() {
   }

   public static boolean isWeapon(ItemStack stack) {
      return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
   }

   public static boolean wouldCrit(LocalPlayer player, Entity target) {
      return player.fallDistance > 0.0
         && !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.isMobilityRestricted()
         && !player.isPassenger()
         && target instanceof LivingEntity
         && !player.isSprinting();
   }

   public static boolean critComing(LocalPlayer player) {
      return !player.onGround() && player.getDeltaMovement().y > 0.0 && !player.onClimbable() && !player.isInWater() && !player.isPassenger();
   }

   public static boolean shieldFacing(LivingEntity target, Player attacker) {
      if (!target.isBlocking()) {
         return false;
      } else {
         Vec3 facing = target.getViewVector(1.0F);
         Vec3 toAttacker = attacker.position().subtract(target.position());
         return new Vec3(facing.x, 0.0, facing.z).dot(new Vec3(toAttacker.x, 0.0, toAttacker.z)) > 0.0;
      }
   }
}
