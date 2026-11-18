package com.jfant.specter.pvp;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.utils.player.InvUtils;
import net.minecraft.world.entity.player.Inventory;

public final class SlotReturn {
   private int saved = -1;
   private int chosen = -1;

   public void take(int slot) {
      if (SpecterCore.mc.player != null && Inventory.isHotbarSlot(slot)) {
         int current = SpecterCore.mc.player.getInventory().getSelectedSlot();
         if (this.saved == -1) {
            this.saved = current;
         }

         if (current != slot) {
            InvUtils.swap(slot, false);
         }

         this.chosen = slot;
      }
   }

   public boolean holding() {
      return this.chosen != -1 && SpecterCore.mc.player != null && SpecterCore.mc.player.getInventory().getSelectedSlot() == this.chosen;
   }

   public void restore(boolean back) {
      if (back && this.holding() && this.saved != this.chosen && Inventory.isHotbarSlot(this.saved)) {
         InvUtils.swap(this.saved, false);
      }

      this.forget();
   }

   public void forget() {
      this.saved = -1;
      this.chosen = -1;
   }

   public boolean active() {
      return this.chosen != -1;
   }

   public int saved() {
      return this.saved;
   }
}
