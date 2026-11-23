package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.auto.inv.InvJob;
import com.jfant.specter.auto.inv.InvSync;
import com.jfant.specter.auto.inv.InvTiming;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import net.minecraft.world.InteractionHand;
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
            .description("When the chestplate is not in your hotbar: open the inventory to move it there, like a player (off: moved without opening anything).")
            .defaultValue(true)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Integer> hotbarSlot = this.sgCrash
      .add(
         new IntSetting.Builder()
            .name("hotbar-slot")
            .description("Hotbar slot (1-9) a chestplate from the inventory is moved to, when no hotbar slot is empty.")
            .defaultValue(9)
            .range(1, 9)
            .sliderRange(1, 9)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Integer> delay = this.sgCrash
      .add(
         new IntSetting.Builder()
            .name("swap-delay")
            .description("Milliseconds between each human step: inventory open, the move, closing, picking the slot, the right click.")
            .defaultValue(60)
            .range(0, 300)
            .sliderRange(0, 200)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Boolean> randomize = this.sgCrash
      .add(
         new BoolSetting.Builder()
            .name("randomize-timings")
            .description("Draw every wait fresh around swap-delay, never the same twice in a row (like Human Totem).")
            .defaultValue(true)
            .visible(this.crashGuard::get)
            .build()
      );
   private final Setting<Integer> variation = this.sgCrash
      .add(
         new IntSetting.Builder()
            .name("timing-variation")
            .description("How far each wait may stray from swap-delay, in percent.")
            .defaultValue(35)
            .range(0, 60)
            .sliderRange(0, 60)
            .visible(() -> this.crashGuard.get() && this.randomize.get())
            .build()
      );
   private final Setting<Boolean> switchBack = this.sgCrash
      .add(
         new BoolSetting.Builder()
            .name("switch-back")
            .description("Go back to the hotbar slot you had once the swap is done.")
            .defaultValue(true)
            .visible(this.crashGuard::get)
            .build()
      );

   /** The human swap: move to the hotbar (if needed), pick the slot, right-click it on, confirm, switch back. */
   private enum Step {
      IDLE,
      MOVING,
      SELECT,
      USE,
      CONFIRM,
      BACK
   }

   private final InvTiming timing = new InvTiming();
   private final SlotReturn slots = new SlotReturn();
   private Step step = Step.IDLE;
   private long stepAt;
   private int useSlot = -1;
   private boolean wantElytra;
   private int tries;
   private int jobToken;

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
      this.finish(false);
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
      this.timing.configure(this.randomize.get(), this.variation.get());
      if (this.step != Step.IDLE) {
         this.runStep();
         return;
      }
      this.glideTicks = this.mc.player.isFallFlying() ? this.glideTicks + 1 : 0;
      if (this.mc.player.isFallFlying() && !this.swapped && this.cooldown == 0 && this.aboutToCrash()) {
         this.equipChestplate();
      }

      if (this.swapped && this.reequip.get()) {
         this.groundTicks = this.mc.player.onGround() ? this.groundTicks + 1 : 0;
         if (this.groundTicks >= 20 + (int)(this.timing.nextMs(this.delay.get() * 4.0, -1.0) / 50.0) && !InvSync.busy(this)) this.equipElytra();
      }
   }

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
      double lead = this.leadTicks();
      double reach = this.distance.get() + speed * (lead + 2.0); // the human swap takes this long to land
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
         if (hit.getLocation().distanceTo(from) <= this.distance.get() + speed * (lead + 1.0)) return true;
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

   /** Roughly how many ticks the human swap takes from now: the crash look-ahead is stretched by this much. */
   private double leadTicks() {
      double d = this.delay.get() * (this.randomize.get() ? 1.0 + this.variation.get() / 100.0 : 1.0);
      int best = this.bestChestplate();
      boolean inHotbar = best >= 0 && Inventory.isHotbarSlot(best);
      double ms = inHotbar ? d * 2.0 : d * (this.openInventory.get() ? 4.0 : 2.5) + 60.0;
      return ms / 50.0 + 1.0;
   }

   private void equipChestplate() {
      int slot = this.bestChestplate();
      if (slot < 0) {
         if (!"none".equals(this.lastAction)) this.warning("About to crash, but there is no chestplate in your inventory.");
         this.lastAction = "none";
         this.cooldown = 40;
         return;
      }
      this.swapped = true;
      this.cooldown = 40;
      this.lastAction = "chestplate";
      this.begin(slot, false);
   }

   private void equipElytra() {
      Inventory inv = this.mc.player.getInventory();
      int slot = -1;
      for (int i = 0; i < 36; i++) {
         if (inv.getItem(i).is(Items.ELYTRA)) {
            // a hotbar elytra (where the chestplate was clicked from) is the natural one to use
            if (slot < 0 || Inventory.isHotbarSlot(i) && !Inventory.isHotbarSlot(slot)) slot = i;
         }
      }
      this.swapped = false;
      this.groundTicks = 0;
      if (slot < 0 || this.chest().is(Items.ELYTRA)) return;
      this.begin(slot, true);
   }

   private boolean isWanted(ItemStack stack) {
      if (this.wantElytra) return stack.is(Items.ELYTRA);
      var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
      return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !stack.is(Items.ELYTRA);
   }

   private long wait(double factor) {
      return System.nanoTime() + this.timing.nextNanos(this.delay.get() * factor, -1.0);
   }

   /**
    * Starts the human swap for the item in inventory slot {@code index}: from the hotbar it is picked and right-clicked
    * on; from the rest of the inventory it is first moved into the hotbar (inventory opened and closed like a player,
    * an empty hotbar slot preferred), then picked and right-clicked. Right-clicking armour swaps it with what is worn.
    */
   private void begin(int index, boolean elytra) {
      this.wantElytra = elytra;
      this.tries = 0;
      if (Inventory.isHotbarSlot(index)) {
         this.useSlot = index;
         this.step = Step.SELECT;
         this.stepAt = this.wait(0.6);
         return;
      }
      int target = this.emptyHotbarSlot();
      if (target < 0) target = this.hotbarSlot.get() - 1;
      int to = target;
      int token = ++this.jobToken;
      InvJob job = InvJob.builder(this, elytra ? "elytra to hotbar" : "chestplate to hotbar")
         .priority(InvJob.Priority.HIGH)
         .openInventory(this.openInventory.get())
         .pace(this.timing, this.delay.get(), this.delay.get(), this.delay.get() * 0.7, -1.0)
         .hotbarSwap(InvSync.menuSlot(index), to, () -> this.mc.player != null && this.isWanted(this.mc.player.getInventory().getItem(index)))
         .onDone(result -> {
            if (token != this.jobToken || this.step != Step.MOVING) return;
            if (!result.outcome().success()) {
               this.warning("Could not move the %s to the hotbar (%s).", elytra ? "elytra" : "chestplate", result.detail());
               this.finish(false);
               return;
            }
            this.useSlot = to;
            this.step = Step.SELECT;
            this.stepAt = this.wait(0.8);
         })
         .build();
      InvSync.submit(job);
      this.step = Step.MOVING;
      this.stepAt = System.nanoTime() + 4_000_000_000L;
   }

   private int emptyHotbarSlot() {
      Inventory inv = this.mc.player.getInventory();
      for (int i = 0; i < Inventory.getSelectionSize(); i++) {
         if (inv.getItem(i).isEmpty()) return i;
      }
      return -1;
   }

   private void runStep() {
      long now = System.nanoTime();
      switch (this.step) {
         case MOVING -> {
            if (now > this.stepAt) {
               this.warning("Moving the item to the hotbar took too long.");
               this.finish(false);
            }
         }
         case SELECT -> {
            if (now < this.stepAt || this.mc.gui.screen() != null) return;
            if (!this.isWanted(this.mc.player.getInventory().getItem(this.useSlot))) {
               this.finish(false);
               return;
            }
            if (!PvpLock.acquire(this)) return;
            this.slots.take(this.useSlot);
            this.step = Step.USE;
            this.stepAt = this.wait(0.8);
         }
         case USE -> {
            if (now < this.stepAt) return;
            if (!this.slots.holding() || !this.isWanted(this.mc.player.getMainHandItem())) {
               this.finish(true);
               return;
            }
            // right click, like a player: armour in hand swaps with what is worn
            if (!Hands.use(InteractionHand.MAIN_HAND)) return;
            this.step = Step.CONFIRM;
            this.stepAt = now + Math.max(300L, Hands.pingMs() * 2L + 150L) * 1_000_000L;
         }
         case CONFIRM -> {
            boolean done = this.wantElytra ? this.chest().is(Items.ELYTRA) : !this.chest().is(Items.ELYTRA) && !this.chest().isEmpty();
            if (done) {
               if (!this.wantElytra) this.info("Crash ahead - chestplate on.");
               this.step = Step.BACK;
               this.stepAt = this.wait(1.0);
            } else if (now > this.stepAt) {
               if (++this.tries < 2) {
                  this.step = Step.USE;
                  this.stepAt = this.wait(0.5);
               } else {
                  this.warning("The %s did not go on.", this.wantElytra ? "elytra" : "chestplate");
                  this.finish(true);
               }
            }
         }
         case BACK -> {
            if (now >= this.stepAt) this.finish(true);
         }
         default -> {
         }
      }
   }

   /** Ends the swap: back to your slot (if wanted) and the hands free for other modules. */
   private void finish(boolean back) {
      this.slots.restore(back && this.switchBack.get());
      PvpLock.release(this);
      this.step = Step.IDLE;
      this.useSlot = -1;
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
