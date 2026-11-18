package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NoInvLeak extends Module {
   private static final int OFFHAND = 40;
   private static final int UNKNOWN = 41;
   private static final Item[] HOTBAR_POOL = new Item[]{
      Items.DIAMOND_SWORD,
      Items.DIAMOND_PICKAXE,
      Items.DIAMOND_AXE,
      Items.DIAMOND_SHOVEL,
      Items.BOW,
      Items.CROSSBOW,
      Items.SHIELD,
      Items.GOLDEN_APPLE,
      Items.COOKED_BEEF,
      Items.ENDER_PEARL,
      Items.WATER_BUCKET,
      Items.TORCH,
      Items.COBBLESTONE,
      Items.OAK_PLANKS,
      Items.ARROW,
      Items.FISHING_ROD,
      Items.SHEARS,
      Items.FLINT_AND_STEEL,
      Items.COMPASS,
      Items.BREAD
   };
   private static final Item[] MAIN_POOL = new Item[]{
      Items.COBBLESTONE,
      Items.DIRT,
      Items.OAK_LOG,
      Items.IRON_INGOT,
      Items.GOLD_INGOT,
      Items.COAL,
      Items.REDSTONE,
      Items.DIAMOND,
      Items.BREAD,
      Items.COOKED_PORKCHOP,
      Items.APPLE,
      Items.ARROW,
      Items.STICK,
      Items.STRING,
      Items.BONE,
      Items.GUNPOWDER,
      Items.LEATHER,
      Items.SAND,
      Items.GRAVEL,
      Items.FLINT,
      Items.CHEST,
      Items.CRAFTING_TABLE,
      Items.FURNACE,
      Items.TORCH,
      Items.WHEAT,
      Items.CARROT,
      Items.POTATO,
      Items.OAK_PLANKS,
      Items.STONE,
      Items.GLASS_BOTTLE,
      Items.PAPER,
      Items.BUCKET
   };
   private static final Item[] OFFHAND_POOL = new Item[]{Items.SHIELD, Items.TORCH, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE, Items.ARROW};
   private static final Item[][] ARMOR_POOL = new Item[][]{
      {Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS},
      {Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS},
      {Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE},
      {Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET}
   };
   private static volatile NoInvLeak active;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<NoInvLeak.Style> style = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("style"))
                  .description(
                     "What is drawn instead of your items. Totem: a totem in every slot. Random: a made-up loadout that stays the same until you switch the module off and on. Empty: nothing."
                  ))
               .defaultValue(NoInvLeak.Style.Totem))
            .build()
      );
   private final Setting<Boolean> fillEmpty = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fill-empty-slots")
            .description("Also put a stand-in in empty slots, so nobody can tell which are empty.")
            .defaultValue(true)
            .visible(() -> this.style.get() != NoInvLeak.Style.Empty)
            .build()
      );
   private final Setting<Boolean> hideTooltips = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("hide-tooltips").description("Do not show the tooltip when you point at one of your items.").defaultValue(true).build()
      );
   private final Setting<Boolean> hideItemName = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-item-name")
            .description("Do not show the item's name above the hotbar when you switch to it.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> hideSelectedSlot = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-selected-slot")
            .description("Do not draw the box round the selected hotbar slot, which would show what you are holding.")
            .defaultValue(true)
            .build()
      );
   private final ItemStack[] loadout = new ItemStack[42];
   private long seed;
   private ItemStack totem;

   public NoInvLeak() {
      super(
         SpecterClientAddon.TOOLS,
         "no-inv-leak",
         "Hides your items on screen for screen shares: fills slots with stand-ins, hides tooltips, the item name and the selected slot."
      );
   }

   @Override
   public void onActivate() {
      this.seed = ThreadLocalRandom.current().nextLong();
      Arrays.fill(this.loadout, null);
      active = this;
   }

   @Override
   public void onDeactivate() {
      active = null;
   }

   public static boolean on() {
      return active != null;
   }

   public static ItemStack slot(ItemStack stack, Slot slot) {
      NoInvLeak module = active;
      return module != null && stack != null && ownSlot(slot) ? module.shown(stack, slot.getContainerSlot()) : stack;
   }

   public static ItemStack cursor(ItemStack stack) {
      NoInvLeak module = active;
      return module != null && stack != null && !stack.isEmpty() ? module.stand(41) : stack;
   }

   public static ItemStack hotbar(ItemStack stack, int seed) {
      NoInvLeak module = active;
      if (module != null && stack != null) {
         int slot = seed == 10 ? 40 : (seed >= 1 && seed <= 9 ? seed - 1 : 41);
         return module.shown(stack, slot);
      } else {
         return stack;
      }
   }

   public static boolean ownSlot(Slot slot) {
      return slot != null && slot.container instanceof Inventory;
   }

   public static boolean hidesTooltips() {
      NoInvLeak module = active;
      return module != null && module.hideTooltips.get();
   }

   public static boolean hidesItemName() {
      NoInvLeak module = active;
      return module != null && module.hideItemName.get();
   }

   public static boolean hidesSelectedSlot() {
      NoInvLeak module = active;
      return module != null && module.hideSelectedSlot.get();
   }

   private ItemStack shown(ItemStack stack, int slot) {
      return !stack.isEmpty() || this.fillEmpty.get() && this.style.get() != NoInvLeak.Style.Empty ? this.stand(slot) : stack;
   }

   private ItemStack stand(int slot) {
      return switch ((NoInvLeak.Style)this.style.get()) {
         case Totem -> {
            if (this.totem == null) {
               this.totem = new ItemStack(Items.TOTEM_OF_UNDYING);
            }

            yield this.totem;
         }
         case Random -> this.loadout(slot);
         case Empty -> ItemStack.EMPTY;
      };
   }

   private ItemStack loadout(int slot) {
      if (slot < 0 || slot > 41) {
         slot = 41;
      }

      ItemStack stack = this.loadout[slot];
      if (stack == null) {
         this.loadout[slot] = stack = this.roll(slot);
      }

      return stack;
   }

   private ItemStack roll(int slot) {
      Item item;
      if (slot < 9) {
         item = this.pick(HOTBAR_POOL, slot);
      } else if (slot < 36 || slot == 41) {
         item = this.pick(MAIN_POOL, slot);
      } else if (slot == 40) {
         item = this.pick(OFFHAND_POOL, slot);
      } else {
         item = ARMOR_POOL[slot - 36][(int)Long.remainderUnsigned(mix(this.seed ^ 167L), ARMOR_POOL[0].length)];
      }

      ItemStack stack = new ItemStack(item);
      if (stack.getMaxStackSize() > 1) {
         stack.setCount(1 + (int)Long.remainderUnsigned(mix(this.seed ^ slot ^ 81L), stack.getMaxStackSize()));
      } else if (stack.isDamageableItem()) {
         stack.setDamageValue((int)Long.remainderUnsigned(mix(this.seed ^ slot ^ 82L), Math.max(1, stack.getMaxDamage() / 2)));
      }

      return stack;
   }

   private Item pick(Item[] pool, int slot) {
      return pool[(int)Long.remainderUnsigned(mix(this.seed ^ slot * -7046029254386353131L), pool.length)];
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }

   public static enum Style {
      Totem("Totem"),
      Random("Random"),
      Empty("Empty");

      private final String label;

      private Style(String label) {
         this.label = label;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }
}
