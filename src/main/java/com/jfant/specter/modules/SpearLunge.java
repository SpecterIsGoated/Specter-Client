package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Spear Lunge: one key swaps to your best Lunge spear, lunges, and swaps back.
 *
 * <p>How it works (the "attribute swap"): the server uses the attributes and enchantments of the item in your selected
 * slot when the attack arrives. So the spear with the highest Lunge level in your hotbar is selected, the piercing
 * attack is sent through the game's own {@code piercingAttack} (exactly what using the spear yourself does), the arm
 * swings, and a few ticks later the slot you had is selected again. Nothing about any item is ever changed - only which
 * slot is selected, and it is always given back (also when the module turns off or you leave the world).
 *
 * <p>Refuses (with a message) when there is no Lunge spear, and skips when a lunge cannot work: in water, while
 * gliding, riding, in a menu, or too hungry to lunge.
 */
public class SpearLunge extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Keybind> key = this.sgGeneral
      .add(new KeybindSetting.Builder().name("lunge-key").description("Press to lunge with your best spear.").defaultValue(Keybind.none()).action(this::request).build());
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("switch-back").description("Go back to the slot you had after the lunge.").defaultValue(true).build());
   private final Setting<Integer> backDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("switch-back-delay")
            .description("Ticks to keep the spear selected after lunging.")
            .defaultValue(2)
            .range(1, 20)
            .sliderRange(1, 10)
            .visible(this.switchBack::get)
            .build()
      );
   private final Setting<Boolean> anySpear = this.sgGeneral
      .add(new BoolSetting.Builder().name("any-spear").description("Also use a spear without Lunge when none has it.").defaultValue(false).build());

   private boolean pending;
   private int pendingTicks;
   private int previous = -1;
   private int taken = -1;
   private int holdTicks;

   public SpearLunge() {
      super(SpecterClientAddon.PVP, "spear-lunge", "One key swaps to your best Lunge spear, lunges, and swaps back. Only the selected slot changes; items are never modified.");
   }

   @Override
   public void onDeactivate() {
      this.giveBack();
      this.pending = false;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.previous = -1;
      this.taken = -1;
      this.pending = false;
   }

   private void request() {
      if (this.mc.player == null || this.mc.gui.screen() != null) return;
      if (this.bestSpear() < 0) {
         this.mc.gui.hud.setOverlayMessage(Component.literal("No Lunge spear in your hotbar"), false);
         return;
      }
      this.pending = true;
      this.pendingTicks = 0;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null || this.mc.gameMode == null || this.mc.level == null) return;
      if (this.taken >= 0 && --this.holdTicks <= 0) this.giveBack();
      if (!this.pending) return;
      if (++this.pendingTicks > 10) {
         this.pending = false; // could not lunge for half a second: give up rather than lunge late
         return;
      }
      if (!this.canLunge()) return;
      int slot = this.bestSpear();
      if (slot < 0) {
         this.pending = false;
         return;
      }
      Inventory inv = this.mc.player.getInventory();
      ItemStack spear = inv.getItem(slot);
      PiercingWeapon weapon = spear.get(DataComponents.PIERCING_WEAPON);
      if (weapon == null) {
         this.pending = false;
         return;
      }
      if (inv.getSelectedSlot() != slot) {
         if (this.taken < 0) this.previous = inv.getSelectedSlot();
         inv.setSelectedSlot(slot);
         this.taken = slot;
      }
      this.pending = false;
      this.mc.gameMode.piercingAttack(spear.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), weapon);
      this.mc.player.swing(InteractionHand.MAIN_HAND, spear.getAttackAnimation(), false);
      this.holdTicks = this.backDelay.get();
   }

   private boolean canLunge() {
      var p = this.mc.player;
      if (this.mc.gui.screen() != null || p.isInWater() || p.isFallFlying() || p.isPassenger() || p.isSpectator()) return false;
      return p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly;
   }

   private void giveBack() {
      if (this.taken >= 0 && this.mc.player != null && this.switchBack.get()) {
         Inventory inv = this.mc.player.getInventory();
         if (inv.getSelectedSlot() == this.taken && this.previous >= 0 && this.previous != this.taken) inv.setSelectedSlot(this.previous);
      }
      this.taken = -1;
      this.previous = -1;
   }

   /** Hotbar slot of the spear with the highest Lunge level (that will not break on this hit), or -1. */
   private int bestSpear() {
      Inventory inv = this.mc.player.getInventory();
      int best = -1;
      int bestLevel = this.anySpear.get() ? -1 : 0;
      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         ItemStack stack = inv.getItem(slot);
         if (!stack.has(DataComponents.PIERCING_WEAPON) || stack.nextDamageWillBreak()) continue;
         int level = lungeLevel(stack);
         if (level > bestLevel || (level == bestLevel && best >= 0 && slot == inv.getSelectedSlot())) {
            best = slot;
            bestLevel = level;
         }
      }
      return best;
   }

   private static int lungeLevel(ItemStack stack) {
      for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
         if (entry.getKey().is(Enchantments.LUNGE)) return entry.getIntValue();
      }
      return 0;
   }
}
