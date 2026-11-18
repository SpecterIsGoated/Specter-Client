package com.jfant.specter.core.utils.player;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.mixin.CreativeInventoryScreenAccessor;
import com.jfant.specter.core.mixin.ItemGroupsAccessor;
import com.jfant.specter.core.mixin.MountScreenHandlerAccessor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;

public class SlotUtils {
   public static final int HOTBAR_START = 0;
   public static final int HOTBAR_END = 8;
   public static final int MAIN_START = 9;
   public static final int MAIN_END = 35;
   public static final int ARMOR_START = 36;
   public static final int ARMOR_END = 39;
   public static final int OFFHAND = 40;

   private SlotUtils() {
   }

   public static int indexToId(int i) {
      if (SpecterCore.mc.player == null) {
         return -1;
      } else {
         AbstractContainerMenu handler = SpecterCore.mc.player.containerMenu;
         if (handler instanceof InventoryMenu) {
            return survivalInventory(i);
         } else if (handler instanceof ItemPickerMenu) {
            return creativeInventory(i);
         } else if (handler instanceof ChestMenu genericContainerScreenHandler) {
            return genericContainer(i, genericContainerScreenHandler.getRowCount());
         } else if (handler instanceof CraftingMenu) {
            return craftingTable(i);
         } else if (handler instanceof FurnaceMenu) {
            return furnace(i);
         } else if (handler instanceof BlastFurnaceMenu) {
            return furnace(i);
         } else if (handler instanceof SmokerMenu) {
            return furnace(i);
         } else if (handler instanceof DispenserMenu) {
            return generic3x3(i);
         } else if (handler instanceof EnchantmentMenu) {
            return enchantmentTable(i);
         } else if (handler instanceof BrewingStandMenu) {
            return brewingStand(i);
         } else if (handler instanceof MerchantMenu) {
            return villager(i);
         } else if (handler instanceof BeaconMenu) {
            return beacon(i);
         } else if (handler instanceof AnvilMenu) {
            return anvil(i);
         } else if (handler instanceof HopperMenu) {
            return hopper(i);
         } else if (handler instanceof ShulkerBoxMenu) {
            return genericContainer(i, 3);
         } else if (handler instanceof HorseInventoryMenu) {
            return horse(handler, i);
         } else if (handler instanceof CartographyTableMenu) {
            return cartographyTable(i);
         } else if (handler instanceof GrindstoneMenu) {
            return grindstone(i);
         } else if (handler instanceof LecternMenu) {
            return lectern();
         } else if (handler instanceof LoomMenu) {
            return loom(i);
         } else if (handler instanceof StonecutterMenu) {
            return stonecutter(i);
         } else if (handler instanceof CrafterMenu) {
            return crafter(i);
         } else {
            return handler instanceof SmithingMenu ? smithingTable(i) : -1;
         }
      }
   }

   private static int survivalInventory(int i) {
      if (isHotbar(i)) {
         return 36 + i;
      } else if (isArmor(i)) {
         return 5 + (i - 36);
      } else {
         return i == 40 ? 45 : i;
      }
   }

   private static int creativeInventory(int i) {
      return CreativeInventoryScreenAccessor.meteor$getSelectedTab() != BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ItemGroupsAccessor.meteor$getInventory())
         ? -1
         : survivalInventory(i);
   }

   private static int genericContainer(int i, int rows) {
      if (isHotbar(i)) {
         return (rows + 3) * 9 + i;
      } else {
         return isMain(i) ? rows * 9 + (i - 9) : -1;
      }
   }

   private static int craftingTable(int i) {
      if (isHotbar(i)) {
         return 37 + i;
      } else {
         return isMain(i) ? i + 1 : -1;
      }
   }

   private static int furnace(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int generic3x3(int i) {
      if (isHotbar(i)) {
         return 36 + i;
      } else {
         return isMain(i) ? i : -1;
      }
   }

   private static int enchantmentTable(int i) {
      if (isHotbar(i)) {
         return 29 + i;
      } else {
         return isMain(i) ? 2 + (i - 9) : -1;
      }
   }

   private static int brewingStand(int i) {
      if (isHotbar(i)) {
         return 32 + i;
      } else {
         return isMain(i) ? 5 + (i - 9) : -1;
      }
   }

   private static int villager(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int beacon(int i) {
      if (isHotbar(i)) {
         return 28 + i;
      } else {
         return isMain(i) ? 1 + (i - 9) : -1;
      }
   }

   private static int anvil(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int hopper(int i) {
      if (isHotbar(i)) {
         return 32 + i;
      } else {
         return isMain(i) ? 5 + (i - 9) : -1;
      }
   }

   private static int horse(AbstractContainerMenu handler, int i) {
      LivingEntity entity = ((MountScreenHandlerAccessor)handler).meteor$getMount();
      if (entity instanceof Llama llamaEntity) {
         int strength = llamaEntity.getStrength();
         if (isHotbar(i)) {
            return 2 + 3 * strength + 28 + i;
         }

         if (isMain(i)) {
            return 2 + 3 * strength + 1 + (i - 9);
         }
      } else if (!(entity instanceof Horse) && !(entity instanceof SkeletonHorse) && !(entity instanceof ZombieHorse) && !(entity instanceof Camel)) {
         if (entity instanceof AbstractChestedHorse abstractDonkeyEntity) {
            boolean chest = abstractDonkeyEntity.hasChest();
            if (isHotbar(i)) {
               return (chest ? 44 : 29) + i;
            }

            if (isMain(i)) {
               return (chest ? 17 : 2) + (i - 9);
            }
         }
      } else {
         if (isHotbar(i)) {
            return 29 + i;
         }

         if (isMain(i)) {
            return 2 + (i - 9);
         }
      }

      return -1;
   }

   private static int cartographyTable(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int grindstone(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int lectern() {
      return -1;
   }

   private static int loom(int i) {
      if (isHotbar(i)) {
         return 31 + i;
      } else {
         return isMain(i) ? 4 + (i - 9) : -1;
      }
   }

   private static int stonecutter(int i) {
      if (isHotbar(i)) {
         return 29 + i;
      } else {
         return isMain(i) ? 2 + (i - 9) : -1;
      }
   }

   private static int crafter(int i) {
      if (isHotbar(i)) {
         return 36 + i;
      } else {
         return isMain(i) ? i : -1;
      }
   }

   private static int smithingTable(int i) {
      if (isHotbar(i)) {
         return 31 + i;
      } else {
         return isMain(i) ? 4 + (i - 9) : -1;
      }
   }

   public static boolean isHotbar(int slotIndex) {
      return slotIndex >= 0 && slotIndex <= 8;
   }

   public static boolean isMain(int slotIndex) {
      return slotIndex >= 9 && slotIndex <= 35;
   }

   public static boolean isArmor(int slotIndex) {
      return slotIndex >= 36 && slotIndex <= 39;
   }
}
