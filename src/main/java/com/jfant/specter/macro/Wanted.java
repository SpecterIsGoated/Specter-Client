package com.jfant.specter.macro;

import com.jfant.specter.ah.VanillaNames;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class Wanted {
   private static final String[] ROMAN = new String[]{"i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x"};
   public final Item item;
   public final String search;
   public final String displayName;
   public final List<Wanted.Enchant> enchants;
   public final boolean exact;
   private final String nameHas;
   private final String nameLacks;

   public Wanted(Item item, String search, String displayName, List<Wanted.Enchant> enchants, boolean exact, String nameHas, String nameLacks) {
      this.item = item;
      this.search = search;
      this.displayName = displayName;
      this.enchants = List.copyOf(enchants);
      this.exact = exact;
      this.nameHas = nameHas;
      this.nameLacks = nameLacks;
   }

   public static Wanted of(Item item, List<Wanted.Enchant> enchants, boolean exact) {
      return new Wanted(item, VanillaNames.search(item), VanillaNames.of(item), enchants, exact, "", "");
   }

   public boolean matches(ItemStack stack) {
      if (!stack.isEmpty() && stack.is(this.item)) {
         if (!this.nameHas.isEmpty() || !this.nameLacks.isEmpty()) {
            String name = Menus.key(stack.getHoverName().getString());
            if (!this.nameHas.isEmpty() && !name.contains(this.nameHas)) {
               return false;
            }

            if (!this.nameLacks.isEmpty() && name.contains(this.nameLacks)) {
               return false;
            }
         }

         return this.enchants.isEmpty() || this.enchantsMatch(stack);
      } else {
         return false;
      }
   }

   private boolean enchantsMatch(ItemStack stack) {
      ItemEnchantments present = enchantmentsOf(stack);
      if (this.exact && present.size() != this.enchants.size()) {
         return false;
      } else {
         for (Wanted.Enchant wanted : this.enchants) {
            int level = levelOf(present, wanted.key());
            if (this.exact ? level != wanted.level() : level < wanted.level()) {
               return false;
            }
         }

         return true;
      }
   }

   private static ItemEnchantments enchantmentsOf(ItemStack stack) {
      return stack.getOrDefault(stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
   }

   private static int levelOf(ItemEnchantments present, String key) {
      for (Entry<Holder<Enchantment>> entry : present.entrySet()) {
         String path = ((Holder)entry.getKey()).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
         if (Menus.key(path).equals(key)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   public String describe() {
      if (this.enchants.isEmpty()) {
         return this.displayName;
      } else {
         List<String> names = new ArrayList<>();

         for (Wanted.Enchant enchant : this.enchants) {
            names.add(enchant.describe());
         }

         return this.displayName + " (" + (this.exact ? "exactly " : "at least ") + String.join(", ", names) + ")";
      }
   }

   public static List<Wanted.Enchant> parseEnchants(String typed) {
      List<Wanted.Enchant> out = new ArrayList<>();
      if (typed == null) {
         return out;
      } else {
         for (String part : typed.split("[,;]")) {
            String text = part.trim();
            if (!text.isEmpty()) {
               int level = 1;
               int space = text.lastIndexOf(32);
               if (space > 0) {
                  int parsed = level(text.substring(space + 1).trim());
                  if (parsed > 0) {
                     level = parsed;
                     text = text.substring(0, space).trim();
                  }
               }

               String key = Menus.key(text);
               if (!key.isEmpty()) {
                  out.add(new Wanted.Enchant(key, text, level));
               }
            }
         }

         return out;
      }
   }

   private static int level(String word) {
      try {
         int value = Integer.parseInt(word);
         return value >= 1 && value <= 10 ? value : 0;
      } catch (NumberFormatException var2) {
         return roman(word);
      }
   }

   public static int roman(String word) {
      String text = Menus.normalize(word);

      for (int i = 0; i < ROMAN.length; i++) {
         if (ROMAN[i].equals(text)) {
            return i + 1;
         }
      }

      return 0;
   }

   public static String withoutRoman(String word) {
      String text = Menus.normalize(word);
      int space = text.lastIndexOf(32);
      return space > 0 && roman(text.substring(space + 1)) > 0 ? text.substring(0, space) : text;
   }

   public record Enchant(String key, String label, int level) {
      public String describe() {
         return this.label + (this.level > 1 ? " " + this.level : "");
      }
   }
}
