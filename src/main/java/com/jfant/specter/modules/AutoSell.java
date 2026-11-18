package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ItemListSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.macro.Menus;
import com.jfant.specter.macro.Pace;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoSell extends Module {
   private static final int CONFIRM_SLOT = 53;
   private static final int MENU_SLOTS = 54;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long WATCH_MS = 350L;
   private static final long COMMAND_COOLDOWN_MS = 4000L;
   private static final int STUCK_CONFIRMS = 3;
   private static final long SKIP_MS = 600000L;
   private static final int MAX_OPEN_FAILURES = 5;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Item>> neverSell = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("never-sell")
            .description("Items that are never put in the sell menu. Everything else in your inventory is sold, hotbar included.")
            .defaultValue(Items.NETHERITE_SWORD, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING)
            .build()
      );
   private final Setting<Boolean> skipHotbar = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("skip-hotbar")
            .description("Also leave everything in your hotbar alone, so the tools you are holding are not sold.")
            .defaultValue(false)
            .build()
      );
   private final Setting<String> menuTitle = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("menu-title")
            .description(
               "A word from the sell menu's title. It only clicks in a menu whose title has it. If /sell opens something and this stops, put part of what it was called here."
            )
            .defaultValue("sell")
            .build()
      );
   private final Pace pace = new Pace();
   private final Map<Item, Long> skipUntil = new HashMap<>();
   private AutoSell.State state = AutoSell.State.IDLE;
   private long nextAt;
   private long lastCommandAt;
   private long openDeadline;
   private int openFailures;
   private boolean warnedOpen;
   private int sync = -1;
   private int confirmedFrom;
   private int lastWaiting;
   private int unchangedConfirms;
   private int sold;

   public AutoSell() {
      super(SpecterClientAddon.DONUT, "auto-sell", "Keeps the /sell menu open and sells everything in your inventory that is not on your never-sell list.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player == null || this.mc.getConnection() == null) {
         this.error("Join a server first.", new Object[0]);
         this.toggle();
      } else if (this.menuTitle.get().isBlank()) {
         this.error("menu-title is empty - put a word from the sell menu's title in it.", new Object[0]);
         this.toggle();
      } else {
         this.state = AutoSell.State.IDLE;
         this.nextAt = 0L;
         this.lastCommandAt = 0L;
         this.openFailures = 0;
         this.warnedOpen = false;
         this.sync = -1;
         this.confirmedFrom = 0;
         this.lastWaiting = 0;
         this.unchangedConfirms = 0;
         this.sold = 0;
         this.skipUntil.clear();
         this.pace.begin();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.mc.player != null && this.ours()) {
         this.mc.player.closeContainer();
      }

      this.sync = -1;
      if (this.sold > 0) {
         this.info("Sold %d item%s.", new Object[]{this.sold, this.sold == 1 ? "" : "s"});
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      this.step();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.step();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      if (this.isActive()) {
         this.toggle();
      }
   }

   private void step() {
      if (this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null && this.mc.gameMode != null) {
         long now = System.currentTimeMillis();
         switch (this.state) {
            case IDLE:
               if (now < this.nextAt) {
                  return;
               }

               this.nextAt = now + 350L;
               if (this.mc.gui.screen() != null || now - this.lastCommandAt < 4000L || !this.hasSellable(now)) {
                  return;
               }

               this.state = AutoSell.State.TYPING;
               this.nextAt = now + this.pace.nextGap(500.0 + 45.0 * "sell".length());
               break;
            case TYPING:
               if (now < this.nextAt) {
                  return;
               }

               if (this.mc.gui.screen() != null) {
                  this.state = AutoSell.State.IDLE;
                  return;
               }

               this.mc.getConnection().sendCommand("sell");
               this.lastCommandAt = now;
               this.openDeadline = now + 10000L;
               this.state = AutoSell.State.OPENING;
               break;
            case OPENING:
               this.opening(now);
               break;
            case FILLING:
               this.filling(now);
               break;
            case CONFIRMING:
               this.confirming(now);
               break;
            case WATCHING:
               this.watching(now);
         }
      }
   }

   private void opening(long now) {
      if (Menus.containerOpen()) {
         if (this.sellMenuOpen()) {
            this.sync = Menus.syncId();
            this.openFailures = 0;
            this.warnedOpen = false;
            this.state = AutoSell.State.FILLING;
            this.nextAt = now + this.pace.nextGap(600.0);
         } else {
            String title = this.mc.gui.screen() instanceof AbstractContainerScreen<?> screen ? screen.getTitle().getString() : "";
            this.warning(
               "/sell opened \"%s\", which does not look like the sell menu, so nothing was clicked. If it is the sell menu, put a word from its title in menu-title.",
               new Object[]{title}
            );
            this.toggle();
         }
      } else if (now > this.openDeadline) {
         this.openFailures++;
         if (!this.warnedOpen) {
            this.warning("The sell menu did not open, trying again.", new Object[0]);
         }

         this.warnedOpen = true;
         if (this.openFailures >= 5) {
            this.error("The sell menu did not open %d times in a row - stopping.", new Object[]{this.openFailures});
            this.toggle();
         } else {
            this.state = AutoSell.State.IDLE;
            this.nextAt = now + this.pace.nextGap(3000.0);
         }
      }
   }

   private void filling(long now) {
      if (this.stillOurs(now)) {
         if (now >= this.nextAt) {
            AbstractContainerMenu handler = this.mc.player.containerMenu;
            Slot next = this.nextSellable(handler, now);
            if (next == null) {
               this.state = AutoSell.State.CONFIRMING;
               this.unchangedConfirms = 0;
               this.nextAt = now + this.pace.nextGap();
            } else {
               this.click(handler, next.index, ContainerInput.QUICK_MOVE);
               this.nextAt = now + this.pace.nextGap();
            }
         }
      }
   }

   private void confirming(long now) {
      if (this.stillOurs(now)) {
         if (now >= this.nextAt) {
            AbstractContainerMenu handler = this.mc.player.containerMenu;
            int waiting = countWaiting(handler);
            if (this.confirmedFrom > 0) {
               int gone = this.confirmedFrom - waiting;
               this.confirmedFrom = 0;
               if (gone > 0) {
                  this.sold += gone;
                  this.mc.gui.hud.setOverlayMessage(Component.literal("Sold " + this.sold + " items this session"), false);
               }
            }

            if (waiting == 0) {
               this.state = AutoSell.State.WATCHING;
               this.nextAt = now + 350L;
            } else if (this.nextSellable(handler, now) != null) {
               this.state = AutoSell.State.FILLING;
               this.nextAt = now + this.pace.nextGap();
            } else {
               this.unchangedConfirms = waiting == this.lastWaiting ? this.unchangedConfirms + 1 : 0;
               this.lastWaiting = waiting;
               if (this.unchangedConfirms >= 3) {
                  this.giveUpOnWaiting(handler, now);
               } else if (handler.slots.get(53).getItem().isEmpty()) {
                  this.nextAt = now + 350L;
               } else {
                  this.click(handler, 53, ContainerInput.PICKUP);
                  this.confirmedFrom = waiting;
                  this.nextAt = now + this.pace.nextGap(700.0);
               }
            }
         }
      }
   }

   private void watching(long now) {
      if (this.stillOurs(now)) {
         if (now >= this.nextAt) {
            this.nextAt = now + 350L;
            AbstractContainerMenu handler = this.mc.player.containerMenu;
            if (this.nextSellable(handler, now) != null) {
               this.state = AutoSell.State.FILLING;
               this.nextAt = now + this.pace.nextGap();
            } else if (countWaiting(handler) > 0) {
               this.state = AutoSell.State.CONFIRMING;
               this.nextAt = now + this.pace.nextGap();
            }
         }
      }
   }

   private void giveUpOnWaiting(AbstractContainerMenu handler, long now) {
      Map<Item, Integer> stuck = waitingItems(handler);

      for (Item item : stuck.keySet()) {
         this.skipUntil.put(item, now + 600000L);
         this.warning("The server would not take %s, skipping it for 10 minutes.", new Object[]{new ItemStack(item).getHoverName().getString()});
      }

      this.mc.player.closeContainer();
      this.sync = -1;
      this.state = AutoSell.State.IDLE;
      this.nextAt = now + this.pace.nextGap(1500.0);
   }

   private boolean stillOurs(long now) {
      if (this.ours()) {
         return true;
      } else if (Menus.containerOpen()) {
         this.warning("Another menu came up (\"%s\") - stopping.", new Object[]{Menus.title()});
         this.toggle();
         return false;
      } else {
         this.sync = -1;
         this.state = AutoSell.State.IDLE;
         this.nextAt = now + this.pace.nextGap(1000.0);
         return false;
      }
   }

   private boolean ours() {
      return this.sync >= 0 && this.sellMenuOpen() && Menus.syncId() == this.sync;
   }

   private boolean sellMenuOpen() {
      return Menus.containerOpen() && Menus.title().contains(Menus.normalize(this.menuTitle.get())) && Menus.contents().size() >= 54;
   }

   private void click(AbstractContainerMenu handler, int slot, ContainerInput type) {
      this.mc.gameMode.handleContainerInput(handler.containerId, slot, 0, type, this.mc.player);
   }

   private Slot nextSellable(AbstractContainerMenu handler, long now) {
      for (Slot slot : handler.slots) {
         if (slot.container instanceof Inventory
            && slot.hasItem()
            && slot.getContainerSlot() < 36
            && this.sellable(slot.getContainerSlot(), slot.getItem(), now)) {
            return slot;
         }
      }

      return null;
   }

   private boolean hasSellable(long now) {
      Inventory inventory = this.mc.player.getInventory();

      for (int i = 0; i < 36; i++) {
         ItemStack stack = inventory.getItem(i);
         if (!stack.isEmpty() && this.sellable(i, stack, now)) {
            return true;
         }
      }

      return false;
   }

   private boolean sellable(int index, ItemStack stack, long now) {
      if (this.skipHotbar.get() && index < 9) {
         return false;
      } else if (this.neverSell.get().contains(stack.getItem())) {
         return false;
      } else {
         Long until = this.skipUntil.get(stack.getItem());
         if (until == null) {
            return true;
         } else if (now < until) {
            return false;
         } else {
            this.skipUntil.remove(stack.getItem());
            return true;
         }
      }
   }

   private static int countWaiting(AbstractContainerMenu handler) {
      int count = 0;

      for (Slot slot : handler.slots) {
         if (!(slot.container instanceof Inventory) && slot.index != 53 && slot.hasItem()) {
            count += slot.getItem().getCount();
         }
      }

      return count;
   }

   private static Map<Item, Integer> waitingItems(AbstractContainerMenu handler) {
      Map<Item, Integer> items = new LinkedHashMap<>();

      for (Slot slot : handler.slots) {
         if (!(slot.container instanceof Inventory) && slot.index != 53 && slot.hasItem()) {
            items.merge(slot.getItem().getItem(), slot.getItem().getCount(), Integer::sum);
         }
      }

      return items;
   }

   @Override
   public String getInfoString() {
      return this.sold + " sold";
   }

   private static enum State {
      IDLE,
      TYPING,
      OPENING,
      FILLING,
      CONFIRMING,
      WATCHING;
   }
}
