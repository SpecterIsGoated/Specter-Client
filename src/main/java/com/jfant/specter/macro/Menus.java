package com.jfant.specter.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public final class Menus {
   private static final Pattern FORMATTING = Pattern.compile("§.");
   private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ";
   private static final String SMALL_CAPS_PLAIN = "abcdefghijklmnoprsstuvwyz";

   private Menus() {
   }

   public static Minecraft mc() {
      return Minecraft.getInstance();
   }

   public static String normalize(String raw) {
      if (raw == null) {
         return "";
      } else {
         String text = FORMATTING.matcher(raw).replaceAll("");
         StringBuilder out = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀѕꜱᴛᴜᴠᴡʏᴢ".indexOf(c);
            if (small >= 0) {
               out.append("abcdefghijklmnoprsstuvwyz".charAt(small));
            } else if (c != 10140 && c != 8594) {
               out.append(c);
            } else {
               out.append('-');
            }
         }

         return out.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
      }
   }

   public static String key(String raw) {
      String text = normalize(raw);
      StringBuilder out = new StringBuilder(text.length());

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            out.append(c);
         }
      }

      return out.toString();
   }

   public static boolean containerOpen() {
      LocalPlayer player = mc().player;
      return player != null && player.containerMenu != player.inventoryMenu && mc().screen instanceof AbstractContainerScreen;
   }

   public static String title() {
      return mc().screen instanceof AbstractContainerScreen<?> screen ? normalize(screen.getTitle().getString()) : "";
   }

   public static int syncId() {
      LocalPlayer player = mc().player;
      return player == null ? -1 : player.containerMenu.containerId;
   }

   public static Menus.Ref ref(Predicate<String> title) {
      return containerOpen() && title.test(title()) ? new Menus.Ref(syncId(), title) : null;
   }

   public static List<ItemStack> contents() {
      List<ItemStack> out = new ArrayList<>();
      LocalPlayer player = mc().player;
      if (player != null && player.containerMenu != player.inventoryMenu) {
         for (Slot slot : player.containerMenu.slots) {
            if (slot.container instanceof Inventory) {
               break;
            }

            out.add(slot.getItem());
         }

         return out;
      } else {
         return out;
      }
   }

   public static ItemStack slot(int index) {
      List<ItemStack> stacks = contents();
      return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
   }

   public static int find(Predicate<ItemStack> test, int limit) {
      List<ItemStack> stacks = contents();

      for (int i = 0; i < stacks.size() && i < limit; i++) {
         if (!stacks.get(i).isEmpty() && test.test(stacks.get(i))) {
            return i;
         }
      }

      return -1;
   }

   public static String fingerprint() {
      StringBuilder text = new StringBuilder().append(syncId()).append('#');

      for (ItemStack stack : contents()) {
         text.append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            .append(':')
            .append(stack.getCount())
            .append(':')
            .append(stack.getHoverName().getString())
            .append(':')
            .append(loreLines(stack).size())
            .append('|');
      }

      return text.toString();
   }

   public static String name(ItemStack stack) {
      return normalize(stack.getHoverName().getString());
   }

   public static List<String> loreLines(ItemStack stack) {
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
         return List.of();
      } else {
         List<String> out = new ArrayList<>(lore.lines().size());

         for (Component line : lore.lines()) {
            out.add(line.getString());
         }

         return out;
      }
   }

   public static String path(ItemStack stack) {
      return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
   }

   public record Ref(int syncId, Predicate<String> titleTest) {
      public boolean open() {
         return Menus.containerOpen() && Menus.syncId() == this.syncId && this.titleTest.test(Menus.title());
      }
   }
}
