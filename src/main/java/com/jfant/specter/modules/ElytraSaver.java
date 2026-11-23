package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.auto.inv.InvJob;
import com.jfant.specter.auto.inv.InvSync;
import com.jfant.specter.core.events.render.Render2DEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.ColorSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.render.NametagUtils;
import com.jfant.specter.core.utils.render.color.SettingColor;
import com.jfant.specter.render.CardText;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Elytra Saver: two things that kill elytra flyers.
 *
 * <ol>
 *   <li>A worn-out elytra: when its durability drops below the threshold a huge warning hangs in the sky ahead of you
 *       (configurable colour and size), plus one chat line.</li>
 *   <li>Flying into a wall: while gliding fast, rays are cast along your velocity (eyes, feet and both sides). When a
 *       block is within the detection distance the best chestplate in your inventory is swapped onto your chest. That
 *       ends the glide, so the impact cannot do elytra "kinetic" damage, and you hit the ground with armour on.</li>
 * </ol>
 *
 * The swap goes through Specter's inventory sync (each click is confirmed by the server before the next), only fires
 * above a minimum speed, once per approach (with a cooldown), and never when no chestplate is available. Optionally the
 * elytra goes back on once you have landed.
 */
public class ElytraSaver extends Module {
   private static final int CHEST_MENU_SLOT = 6;
   private final SettingGroup sgWarning = this.settings.createGroup("Low Durability");
   private final SettingGroup sgCrash = this.settings.createGroup("Crash Guard");
   private final Setting<Boolean> warn = this.sgWarning
      .add(new BoolSetting.Builder().name("warning").description("Show the warning in the sky when your elytra is nearly broken.").defaultValue(true).build());
   private final Setting<Integer> threshold = this.sgWarning
      .add(new IntSetting.Builder().name("threshold").description("Warn below this much durability left, in percent.").defaultValue(10).range(1, 50).sliderRange(1, 50).build());
   private final Setting<SettingColor> warnColor = this.sgWarning
      .add(new ColorSetting.Builder().name("warning-colour").description("Colour of the warning.").defaultValue(new SettingColor(255, 60, 60)).build());
   private final Setting<Double> warnSize = this.sgWarning
      .add(new DoubleSetting.Builder().name("warning-size").description("Size of the warning text.").defaultValue(5.0).range(1.0, 12.0).sliderRange(1.0, 10.0).build());
   private final Setting<Boolean> crashGuard = this.sgCrash
      .add(new BoolSetting.Builder().name("crash-guard").description("Swap a chestplate on when you are about to fly into a block.").defaultValue(true).build());
   private final Setting<Double> distance = this.sgCrash
      .add(
         new DoubleSetting.Builder()
            .name("detect-distance")
            .description("Blocks ahead (along your flight) that count as about to crash.")
            .defaultValue(5.0)
            .range(1.5, 20.0)
            .sliderRange(1.5, 15.0)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Double> minSpeed = this.sgCrash
      .add(
         new DoubleSetting.Builder()
            .name("min-speed")
            .description("Only act above this speed (blocks per second). Slow bumps do not hurt.")
            .defaultValue(14.0)
            .range(4.0, 60.0)
            .sliderRange(4.0, 40.0)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Boolean> reequip = this.sgCrash
      .add(
         new BoolSetting.Builder()
            .name("re-equip-elytra")
            .description("Put the elytra back on after you have landed safely.")
            .defaultValue(true)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Boolean> openInventory = this.sgCrash
      .add(
         new BoolSetting.Builder()
            .name("open-inventory")
            .description("Open the inventory for the swap like a player would (slower, safest on strict servers).")
            .defaultValue(false)
            .visible(this.crashGuard::get)
            .build()
      );

   private boolean warned;
   private boolean swapped;
   /** Ticks since this glide started. */
   private int glideTicks;
   private int cooldown;
   private int groundTicks;
   private String lastAction = "";
   private final Vector3d anchor = new Vector3d();
   private float anchorYaw;

   public ElytraSaver() {
      super(SpecterClientAddon.TOOLS, "elytra-saver", "Warns in the sky when your elytra is almost broken, and swaps a chestplate on when you are about to fly into a wall.");
   }

   @Override
   public void onActivate() {
      this.warned = false;
      this.swapped = false;
      this.cooldown = 0;
      this.groundTicks = 0;
      if (this.mc.player != null) this.anchorYaw = this.mc.player.getYRot();
   }

   @Override
   public void onDeactivate() {
      InvSync.cancel(this);
   }

   private ItemStack chest() {
      return this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
   }

   /** Remaining durability in percent, or -1 when no elytra is worn. */
   private int elytraPercent() {
      ItemStack chest = this.chest();
      if (!chest.is(Items.ELYTRA) || chest.getMaxDamage() <= 0) return -1;
      return Math.round(100.0F * (chest.getMaxDamage() - chest.getDamageValue()) / chest.getMaxDamage());
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null) return;
      if (this.cooldown > 0) this.cooldown--;
      int pct = this.elytraPercent();
      if (pct >= 0 && pct <= this.threshold.get()) {
         if (!this.warned && this.warn.get()) this.warning("Elytra at %d%% durability - land and repair it.", pct);
         this.warned = true;
      } else if (pct > this.threshold.get()) {
         this.warned = false;
      }

      if (!this.crashGuard.get()) return;
      this.glideTicks = this.mc.player.isFallFlying() ? this.glideTicks + 1 : 0;
      if (this.mc.player.isFallFlying() && !this.swapped && this.cooldown == 0 && this.aboutToCrash()) {
         this.equipChestplate();
      }

      if (this.swapped && this.reequip.get()) {
         this.groundTicks = this.mc.player.onGround() ? this.groundTicks + 1 : 0;
         if (this.groundTicks >= 20 && !InvSync.busy(this)) this.equipElytra();
      }
   }

   /** Rays along the velocity from the eyes, the feet and both shoulders. */
   /**
    * Only walls count: elytra "kinetic" damage comes from hitting something sideways, so the rays follow the
    * HORIZONTAL velocity and a hit on the top of a block (the ground) is ignored. Never in the first second and a half
    * of a glide - at take-off the ground is right there, and swapping then would stop you from ever getting up.
    */
   private boolean aboutToCrash() {
      if (this.glideTicks < 30) return false;
      Vec3 v = this.mc.player.getDeltaMovement();
      double speed = Math.sqrt(v.x * v.x + v.z * v.z);
      if (speed * 20.0 < this.minSpeed.get() || speed < 1.0E-4) return false;
      Vec3 dir = new Vec3(v.x / speed, 0.0, v.z / speed);
      double reach = this.distance.get() + speed * 2.0; // two ticks of travel for the swap to land
      Vec3 eye = this.mc.player.getEyePosition();
      Vec3 feet = this.mc.player.position().add(0.0, 0.3, 0.0);
      Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
      if (side.lengthSqr() < 1.0E-6) side = new Vec3(1.0, 0.0, 0.0);
      side = side.normalize().scale(0.4);
      Vec3[] origins = {eye, feet, eye.add(side), eye.subtract(side)};
      for (Vec3 from : origins) {
         Vec3 to = from.add(dir.scale(reach));
         BlockHitResult hit = this.mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mc.player));
         if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() == net.minecraft.core.Direction.UP) continue;
         if (hit.getLocation().distanceTo(from) <= this.distance.get() + speed) return true;
      }
      return false;
   }

   /** Inventory index (0-35) of the best chestplate (most armour, then toughness), or -1. */
   private int bestChestplate() {
      Inventory inv = this.mc.player.getInventory();
      int best = -1;
      double bestScore = 0.0;
      for (int i = 0; i < 36; i++) {
         ItemStack stack = inv.getItem(i);
         var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
         if (equippable == null || equippable.slot() != EquipmentSlot.CHEST || stack.is(Items.ELYTRA)) continue;
         if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 1) continue;
         double[] score = {0.0};
         stack.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) score[0] += modifier.amount() * 10.0;
            else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) score[0] += modifier.amount();
         });
         if (score[0] > bestScore) {
            bestScore = score[0];
            best = i;
         }
      }
      return best;
   }

   private void equipChestplate() {
      int slot = this.bestChestplate();
      if (slot < 0) {
         if (!"none".equals(this.lastAction)) this.warning("About to crash, but there is no chestplate in your inventory.");
         this.lastAction = "none";
         this.cooldown = 40;
         return;
      }
      this.swapInto(slot, "chestplate on (crash guard)", () -> this.chest().is(Items.ELYTRA));
      this.swapped = true;
      this.cooldown = 40;
      this.lastAction = "chestplate";
      this.info("Crash ahead - chestplate on.");
   }

   private void equipElytra() {
      Inventory inv = this.mc.player.getInventory();
      int slot = -1;
      for (int i = 0; i < 36; i++) {
         if (inv.getItem(i).is(Items.ELYTRA)) {
            slot = i;
            break;
         }
      }
      this.swapped = false;
      this.groundTicks = 0;
      if (slot < 0 || this.chest().is(Items.ELYTRA)) return;
      this.swapInto(slot, "elytra back on", () -> !this.chest().is(Items.ELYTRA));
   }

   /**
    * Swaps inventory slot {@code index} with the chest armour slot. A hotbar item swaps in one click; anything else
    * goes through hotbar slot 9 and is put back, so nothing ends up on the cursor or in the wrong place.
    */
   private void swapInto(int index, String label, java.util.function.BooleanSupplier guard) {
      InvJob.Builder job = InvJob.builder(this, label).priority(InvJob.Priority.HIGH).openInventory(this.openInventory.get());
      if (Inventory.isHotbarSlot(index)) {
         job.click(CHEST_MENU_SLOT, index, ContainerInput.SWAP, guard);
      } else {
         int via = 8;
         int menu = InvSync.menuSlot(index);
         job.hotbarSwap(menu, via, () -> true);
         job.click(CHEST_MENU_SLOT, via, ContainerInput.SWAP, () -> true);
         job.hotbarSwap(menu, via, () -> true);
      }
      InvSync.submit(job.build());
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mc.player == null || !this.warn.get()) return;
      int pct = this.elytraPercent();
      if (pct < 0 || pct > this.threshold.get()) return;
      // the sign hangs in the sky ahead, following where you fly a little behind (so it feels placed in the world)
      float yaw = this.mc.player.getYRot();
      this.anchorYaw += net.minecraft.util.Mth.wrapDegrees(yaw - this.anchorYaw) * 0.04F;
      double rad = Math.toRadians(this.anchorYaw);
      Vec3 eye = this.mc.player.getEyePosition(event.tickDelta);
      this.anchor.set(eye.x - Math.sin(rad) * 60.0, eye.y + 26.0, eye.z + Math.cos(rad) * 60.0);
      if (!NametagUtils.to2D(this.anchor, this.warnSize.get(), false)) return;
      GuiGraphicsExtractor ctx = event.graphics;
      int base = this.warnColor.get().getPacked();
      float pulse = (float)(0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 160.0));
      int color = (Math.round(255 * pulse) << 24) | (base & 0xFFFFFF);
      ItemStack chest = this.chest();
      String title = "ELYTRA LOW";
      String detail = pct + "% - " + (chest.getMaxDamage() - chest.getDamageValue()) + " uses left";
      NametagUtils.begin(this.anchor, ctx);
      CardText.draw(ctx, title, -CardText.width(title) / 2.0F, -14.0F, color, true);
      ctx.pose().pushMatrix();
      ctx.pose().scale(0.45F, 0.45F);
      CardText.draw(ctx, detail, -CardText.width(detail) / 2.0F, 2.0F, 0xFFFFFFFF, true);
      ctx.pose().popMatrix();
      NametagUtils.end(ctx);
   }
}
