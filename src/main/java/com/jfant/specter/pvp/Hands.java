package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.entity.DamageUtils;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult.Success;
import net.minecraft.world.InteractionResult.SwingSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class Hands {
   public static final int READY = -2;
   public static final int NONE = -1;

   private Hands() {
   }

   public static boolean canAct() {
      LocalPlayer player = SpecterCore.mc.player;
      if (player != null && SpecterCore.mc.level != null && SpecterCore.mc.gameMode != null && SpecterCore.mc.gui.screen() == null) {
         return player.isAlive() && !player.isSpectator() && SpecterCore.mc.gameMode.getPlayerMode() != GameType.SPECTATOR ? !player.isUsingItem() : false;
      } else {
         return false;
      }
   }

   public static int hotbarSlot(Item item) {
      return hotbarSlot(stack -> stack.is(item));
   }

   public static int hotbarSlot(Predicate<ItemStack> wanted) {
      if (SpecterCore.mc.player == null) {
         return -1;
      } else {
         Inventory inventory = SpecterCore.mc.player.getInventory();

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (wanted.test(inventory.getItem(slot))) {
               return slot;
            }
         }

         return -1;
      }
   }

   public static int count(Item item) {
      if (SpecterCore.mc.player == null) {
         return 0;
      } else {
         Inventory inventory = SpecterCore.mc.player.getInventory();
         int total = 0;

         for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
               total += stack.getCount();
            }
         }

         return total;
      }
   }

   public static int route(Item item) {
      LocalPlayer player = SpecterCore.mc.player;
      if (player == null) {
         return -1;
      } else if (player.getMainHandItem().is(item)) {
         return -2;
      } else {
         return player.getOffhandItem().is(item) && passesClick(player.getMainHandItem()) ? -2 : hotbarSlot(item);
      }
   }

   public static boolean passesClick(ItemStack stack) {
      return stack.isEmpty() || stack.is(ItemTags.SWORDS) || stack.is(Items.MACE) || stack.is(Items.TOTEM_OF_UNDYING);
   }

   public static boolean startsFrom(ItemStack main, Item... own) {
      for (Item item : own) {
         if (main.is(item)) {
            return true;
         }
      }

      return passesClick(main) || Melee.isWeapon(main);
   }

   public static InteractionHand handOf(Item item) {
      return SpecterCore.mc.player != null && !SpecterCore.mc.player.getMainHandItem().is(item) && SpecterCore.mc.player.getOffhandItem().is(item)
         ? InteractionHand.OFF_HAND
         : InteractionHand.MAIN_HAND;
   }

   public static HitResult crosshair() {
      HitResult hit = SpecterCore.mc.hitResult;
      return hit instanceof EntityHitResult entityHit && !(entityHit.getEntity() instanceof EndCrystal) && SpecterCore.mc.player != null
         ? SpecterCore.mc.player.pick(SpecterCore.mc.player.blockInteractionRange(), 1.0F, false)
         : hit;
   }

   public static boolean useOn(InteractionHand hand, BlockHitResult hit) {
      LocalPlayer player = SpecterCore.mc.player;
      if (player != null && SpecterCore.mc.gameMode != null && !PvpLock.actedThisTick()) {
         if (SpecterCore.mc.gameMode.useItemOn(player, hand, hit) instanceof Success success && success.swingSource() == SwingSource.CLIENT) {
            player.swing(hand);
         }

         PvpLock.claimTick();
         return true;
      } else {
         return false;
      }
   }

   public static boolean use(InteractionHand hand) {
      LocalPlayer player = SpecterCore.mc.player;
      if (player != null && SpecterCore.mc.gameMode != null && !PvpLock.actedThisTick()) {
         if (SpecterCore.mc.gameMode.useItem(player, hand) instanceof Success success && success.swingSource() == SwingSource.CLIENT) {
            player.swing(hand);
         }

         PvpLock.claimTick();
         return true;
      } else {
         return false;
      }
   }

   public static boolean attack(Entity target) {
      LocalPlayer player = SpecterCore.mc.player;
      if (player != null && SpecterCore.mc.gameMode != null && target != null && !target.isRemoved() && !PvpLock.actedThisTick()) {
         SpecterCore.mc.gameMode.attack(player, target);
         player.swing(InteractionHand.MAIN_HAND);
         PvpLock.claimTick();
         return true;
      } else {
         return false;
      }
   }

   public static int damagingRoute(Entity target) {
      LocalPlayer player = SpecterCore.mc.player;
      if (player == null) {
         return -1;
      } else if (DamageUtils.getAttackDamage(player, target, player.getMainHandItem()) > 0.0F) {
         return -2;
      } else {
         int best = -1;
         float bestDamage = 0.0F;

         for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            float damage = DamageUtils.getAttackDamage(player, target, player.getInventory().getItem(slot));
            if (damage > bestDamage) {
               best = slot;
               bestDamage = damage;
            }
         }

         return best;
      }
   }

   public static boolean coolingDown(ItemStack stack) {
      return SpecterCore.mc.player != null && SpecterCore.mc.player.getCooldowns().isOnCooldown(stack);
   }

   public static int pingMs() {
      if (SpecterCore.mc.getConnection() != null && SpecterCore.mc.player != null) {
         PlayerInfo entry = SpecterCore.mc.getConnection().getPlayerInfo(SpecterCore.mc.player.getUUID());
         return entry == null ? 0 : Math.max(0, entry.getLatency());
      } else {
         return 0;
      }
   }

   public static int confirmTicks() {
      return Math.max(4, Math.min(20, pingMs() / 50 + 1 + 2));
   }
}
