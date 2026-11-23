package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Legit Auto Tool: while you hold attack on a block, picks the hotbar slot that breaks it fastest - the way the game
 * works it out (tool speed, Efficiency, Haste, Mining Fatigue, underwater and in-air penalties, whether the tool can
 * harvest it) - and only by changing the selected hotbar slot, like a player scrolling. A short human delay before each
 * switch, no switching for the same block twice, tools about to break are skipped, and the slot you had goes back when
 * you stop mining.
 */
public class LegitAutoTool extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("switch-delay").description("Ticks to wait before switching, like a person reaching for the key. 0 is instant.").defaultValue(2).range(0, 10).sliderRange(0, 10).build());
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("switch-back").description("Go back to the slot you had once you stop mining.").defaultValue(true).build());
   private final Setting<Integer> keepDurability = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("save-tools")
            .description("Never pick a tool with this much durability or less left (0 lets tools break).")
            .defaultValue(3)
            .range(0, 50)
            .sliderRange(0, 30)
            .build()
      );
   private final Setting<Boolean> onlyBetter = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-if-much-faster")
            .description("Only switch when the other tool is at least 25% faster, so it does not flick between nearly equal tools.")
            .defaultValue(true)
            .build()
      );

   private BlockPos target;
   private int pending = -1;
   private int wait;
   private int origin = -1;
   private int ours = -1;
   private int backWait;

   public LegitAutoTool() {
      super(SpecterClientAddon.TOOLS, "legit-auto-tool", "While you mine, switches to the fastest tool in your hotbar the way a player would (hotbar scroll, short delay), and back when you stop.");
   }

   @Override
   public void onActivate() {
      this.reset();
   }

   @Override
   public void onDeactivate() {
      if (this.origin >= 0 && this.mc.player != null && this.mc.player.getInventory().getSelectedSlot() == this.ours) this.select(this.origin);
      this.reset();
   }

   private void reset() {
      this.target = null;
      this.pending = -1;
      this.wait = 0;
      this.origin = -1;
      this.ours = -1;
      this.backWait = 0;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
         this.reset();
         return;
      }
      Inventory inv = this.mc.player.getInventory();
      int selected = inv.getSelectedSlot();
      // the player picked a slot themselves: that is now the slot to return to
      if (this.ours >= 0 && selected != this.ours) {
         this.origin = -1;
         this.ours = -1;
      }

      BlockPos aimed = this.mc.gui.screen() == null && this.mc.options.keyAttack.isDown() && this.mc.hitResult instanceof BlockHitResult hit
         && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
      BlockState state = aimed == null ? null : this.mc.level.getBlockState(aimed);
      if (aimed == null || state.isAir() || this.mc.gameMode.getPlayerMode().isCreative()) {
         this.target = null;
         this.pending = -1;
         this.giveBack();
         return;
      }

      if (!aimed.equals(this.target)) {
         this.target = aimed.immutable();
         this.pending = -1;
      }
      int best = this.bestSlot(state, aimed, selected);
      if (best == selected) {
         this.pending = -1;
         return;
      }
      if (this.pending != best) {
         this.pending = best;
         this.wait = this.delay.get();
      }
      if (this.wait > 0) {
         this.wait--;
         return;
      }
      if (this.origin < 0) this.origin = selected;
      this.select(best);
      this.ours = best;
      this.pending = -1;
      this.backWait = this.delay.get();
   }

   private void giveBack() {
      if (this.origin < 0 || !this.switchBack.get()) {
         if (!this.switchBack.get()) {
            this.origin = -1;
            this.ours = -1;
         }
         return;
      }
      if (this.backWait > 0) {
         this.backWait--;
         return;
      }
      if (this.mc.player.getInventory().getSelectedSlot() == this.ours) this.select(this.origin);
      this.origin = -1;
      this.ours = -1;
   }

   private void select(int slot) {
      this.mc.player.getInventory().setSelectedSlot(slot);
   }

   private boolean usable(ItemStack stack) {
      int keep = this.keepDurability.get();
      return !stack.isDamageableItem() || keep <= 0 || stack.getMaxDamage() - stack.getDamageValue() > keep;
   }

   private int bestSlot(BlockState state, BlockPos pos, int selected) {
      Inventory inv = this.mc.player.getInventory();
      ItemStack held = inv.getItem(selected);
      float heldSpeed = this.usable(held) ? this.breakSpeed(held, state, pos) : 0.0F;
      int best = selected;
      float bestSpeed = heldSpeed;
      for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
         if (slot == selected) continue;
         ItemStack stack = inv.getItem(slot);
         if (stack.isEmpty() || !this.usable(stack)) continue;
         float speed = this.breakSpeed(stack, state, pos);
         if (speed > bestSpeed) {
            bestSpeed = speed;
            best = slot;
         }
      }
      if (best != selected && this.onlyBetter.get() && heldSpeed > 0.0F && bestSpeed < heldSpeed * 1.25F) return selected;
      return best;
   }

   /** Fraction of the block broken per tick with this stack, like the game's own calculation. */
   private float breakSpeed(ItemStack stack, BlockState state, BlockPos pos) {
      float hardness = state.getDestroySpeed(this.mc.level, pos);
      if (hardness < 0.0F) return 0.0F;
      if (hardness == 0.0F) return 1.0F;
      float speed = stack.getDestroySpeed(state);
      if (speed > 1.0F) {
         double[] bonus = {0.0};
         stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.MINING_EFFICIENCY)) bonus[0] += modifier.amount();
         });
         speed += (float)bonus[0];
      }
      if (MobEffectUtil.hasDigSpeed(this.mc.player)) speed *= 1.0F + (MobEffectUtil.getDigSpeedAmplification(this.mc.player) + 1) * 0.2F;
      MobEffectInstance fatigue = this.mc.player.getEffect(MobEffects.MINING_FATIGUE);
      if (fatigue != null) {
         speed *= switch (fatigue.getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }
      speed *= (float)this.mc.player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
      if (this.mc.player.isEyeInFluid(FluidTags.WATER)) speed *= (float)this.mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
      if (!this.mc.player.onGround()) speed /= 5.0F;
      boolean correct = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
      return speed / hardness / (correct ? 30.0F : 100.0F);
   }
}
