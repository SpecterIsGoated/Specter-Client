package com.jfant.specter.macro.ah;

import com.jfant.specter.macro.Menus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public final class Ah {
   public static final int LISTINGS = 45;
   public static final int FILTER_SLOT = 47;
   public static final int REFRESH_SLOT = 49;
   public static final int YOUR_ITEMS_SLOT = 51;
   private static final int LORE_DEFAULT = 11141290;
   private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");

   private Ah() {
   }

   public static boolean isListing(String title) {
      return title.startsWith("auction (page");
   }

   public static boolean isAuction(String title) {
      return title.startsWith("auction");
   }

   public static boolean isConfirm(String title) {
      return title.contains("are you sure you want to buy");
   }

   public static boolean isOrders(String title) {
      return title.contains("order");
   }

   public static int refreshSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         if (!stacks.get(i).isEmpty() && stacks.get(i).is(Items.ANVIL)) {
            return i;
         }
      }

      return -1;
   }

   public static int filterSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && (stack.is(Items.HOPPER) || Menus.name(stack).contains("filter"))) {
            return i;
         }
      }

      return stacks.size() > 47 && !stacks.get(47).isEmpty() ? 47 : -1;
   }

   public static int yourItemsSlot(List<ItemStack> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         ItemStack stack = stacks.get(i);
         if (!stack.isEmpty() && (stack.is(Items.CHEST) || stack.is(Items.TRAPPED_CHEST))) {
            return i;
         }
      }

      return -1;
   }

   public static Ah.Sort selectedSort(ItemStack filter) {
      List<Ah.Sort> options = new ArrayList<>();
      List<Integer> colors = new ArrayList<>();
      ItemLore lore = filter.get(DataComponents.LORE);
      if (lore == null) {
         return Ah.Sort.UNKNOWN;
      } else {
         for (Component line : lore.lines()) {
            String text = Menus.normalize(line.getString());
            Ah.Sort option = text.contains("recent")
               ? Ah.Sort.RECENT
               : (text.contains("lowest") ? Ah.Sort.LOWEST : (text.contains("highest") ? Ah.Sort.HIGHEST : Ah.Sort.UNKNOWN));
            if (option != Ah.Sort.UNKNOWN) {
               options.add(option);
               colors.add(colorOf(line));
            }
         }

         if (options.size() < 2) {
            return Ah.Sort.UNKNOWN;
         } else {
            for (int i = 0; i < options.size(); i++) {
               boolean unique = true;
               boolean othersAgree = true;
               int other = -2;

               for (int j = 0; j < options.size(); j++) {
                  if (j != i) {
                     if (colors.get(j).equals(colors.get(i))) {
                        unique = false;
                     }

                     if (other == -2) {
                        other = colors.get(j);
                     } else if (other != colors.get(j)) {
                        othersAgree = false;
                     }
                  }
               }

               if (unique && othersAgree) {
                  return options.get(i);
               }
            }

            int best = -1;
            Ah.Sort pick = Ah.Sort.UNKNOWN;

            for (int i = 0; i < options.size(); i++) {
               int rgb = colors.get(i);
               int brightness = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
               if (brightness > best) {
                  best = brightness;
                  pick = options.get(i);
               } else if (brightness == best) {
                  pick = Ah.Sort.UNKNOWN;
               }
            }

            return pick;
         }
      }
   }

   private static int colorOf(Component line) {
      int[] value = new int[]{-1};
      line.visit((style, piece) -> {
         String trimmed = piece.trim();
         if (value[0] < 0 && !trimmed.isEmpty() && !trimmed.equals("•") && !trimmed.equals("▪")) {
            TextColor color = style.getColor();
            value[0] = color == null ? 11141290 : color.getValue();
            return Optional.of(Boolean.TRUE);
         } else {
            return Optional.empty();
         }
      }, Style.EMPTY);
      return value[0];
   }

   public static int minutesLeft(ItemStack stack) {
      for (String line : Menus.loreLines(stack)) {
         String clean = Menus.normalize(line);
         if (clean.contains("self destruct") || clean.contains("expire") || clean.contains("time left") || clean.contains("ends in")) {
            Matcher m = DURATION.matcher(clean);
            int minutes = 0;
            boolean found = false;

            while (m.find()) {
               found = true;
               int value = Integer.parseInt(m.group(1));
               String var8 = m.group(2);
               switch (var8) {
                  case "d":
                     minutes += value * 1440;
                     break;
                  case "h":
                     minutes += value * 60;
                     break;
                  case "m":
                     minutes += value;
               }
            }

            if (found) {
               return minutes;
            }
         }
      }

      return -1;
   }

   public static enum Sort {
      UNKNOWN,
      LOWEST,
      HIGHEST,
      RECENT;
   }
}
