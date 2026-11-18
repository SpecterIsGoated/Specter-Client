package com.jfant.specter.macro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;

public final class Hand {
   private final MacroThread worker;
   private final Pace pace;
   private long notBefore;

   public Hand(MacroThread worker, Pace pace) {
      this.worker = worker;
      this.pace = pace;
   }

   public boolean click(Menus.Ref menu, int slot) {
      return this.click(menu, slot, 0, ContainerInput.PICKUP);
   }

   public boolean click(Menus.Ref menu, int slot, int button, ContainerInput type) {
      this.waitTurn();
      boolean sent = this.worker.call(() -> {
         Minecraft mc = Menus.mc();
         LocalPlayer player = mc.player;
         if (player == null || mc.gameMode == null || !menu.open()) {
            return false;
         } else if (slot >= 0 && slot < player.containerMenu.slots.size()) {
            mc.gameMode.handleInventoryMouseClick(menu.syncId(), slot, button, type, player);
            return true;
         } else {
            return false;
         }
      });
      if (sent) {
         this.rest();
      }

      return sent;
   }

   public boolean clickInventory(int slot, int button, ContainerInput type) {
      this.waitTurn();
      boolean sent = this.worker.call(() -> {
         Minecraft mc = Menus.mc();
         LocalPlayer player = mc.player;
         if (player != null && mc.gameMode != null && mc.gui.screen() instanceof InventoryScreen) {
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, button, type, player);
            return true;
         } else {
            return false;
         }
      });
      if (sent) {
         this.rest();
      }

      return sent;
   }

   public void openInventory() {
      this.waitTurn();
      this.worker.run(() -> {
         Minecraft mc = Menus.mc();
         if (mc.player != null && mc.gui.screen() == null) {
            mc.gui.setScreen(new InventoryScreen(mc.player));
         }
      });
      this.rest();
   }

   public void closeScreen() {
      if (this.worker.call(() -> Menus.mc().gui.screen() != null)) {
         this.waitTurn();
         this.worker.run(() -> {
            Minecraft mc = Menus.mc();
            if (mc.player != null && mc.gui.screen() != null) {
               if (Menus.containerOpen()) {
                  mc.player.closeContainer();
               } else {
                  mc.gui.screen().onClose();
               }
            }
         });
         this.rest();
      }
   }

   public void command(String command) {
      this.closeScreen();
      this.waitTurn();
      this.worker.sleep(this.pace.nextGap(500.0 + 45.0 * command.length()));
      this.worker.run(() -> {
         ClientPacketListener handler = Menus.mc().getConnection();
         if (handler != null && Menus.mc().gui.screen() == null) {
            handler.sendCommand(command);
         }
      });
      this.rest();
   }

   public void waitTurn() {
      long wait = this.notBefore - System.currentTimeMillis();
      if (wait > 0L) {
         this.worker.sleep(wait);
      }
   }

   public void rest() {
      this.notBefore = System.currentTimeMillis() + this.pace.nextGap();
   }

   public Pace pace() {
      return this.pace;
   }
}
