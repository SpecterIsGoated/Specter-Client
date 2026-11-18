package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.player.FindItemResult;
import com.jfant.specter.core.utils.player.InvUtils;
import com.jfant.specter.core.utils.player.PlayerUtils;
import com.jfant.specter.util.Trigger;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

public class PearlMethod extends Module {
   private static final long LOOK_NANOS = 500000000L;
   private static final long SELECT_NANOS = 500000000L;
   private static final long THROW_NANOS = 500000000L;
   private static final long RTP_NANOS = 100000000L;
   private static final double PEARL_RISE = 29.45;
   private static final double THROW_BELOW_EYES = 0.1;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Trigger trigger = new Trigger(this.sgGeneral, "throws a pearl up and runs /rtp", -1);
   private PearlMethod.Step step = PearlMethod.Step.IDLE;
   private long stepStart;
   private float startPitch;

   public PearlMethod() {
      super(
         SpecterClientAddon.TOOLS,
         "pearl-method",
         "Centres you on your block, looks up, throws an ender pearl, then runs /rtp - on a key press or when you go down to a height."
      );
   }

   @Override
   public void onActivate() {
      this.step = PearlMethod.Step.IDLE;
      this.stepStart = 0L;
      this.trigger.reset();
      this.info("%s", new Object[]{this.trigger.waitingLine()});
   }

   @Override
   public void onDeactivate() {
      this.step = PearlMethod.Step.IDLE;
      this.stepStart = 0L;
   }

   @EventHandler(
      priority = -200
   )
   private void onTickPre(TickEvent.Pre event) {
      if (this.step != PearlMethod.Step.IDLE && this.mc.player != null && this.mc.gameMode != null) {
         this.mc.options.keyAttack.setDown(false);
         if (this.mc.gameMode.isDestroying()) {
            this.mc.gameMode.stopDestroyBlock();
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null || this.mc.getConnection() == null) {
         this.step = PearlMethod.Step.IDLE;
      } else if (this.step == PearlMethod.Step.IDLE) {
         if (this.trigger.poll(this.mc)) {
            this.next(PearlMethod.Step.START);
         }
      } else {
         if (this.step == PearlMethod.Step.HOLD_SELECT || this.step == PearlMethod.Step.HOLD_THROW) {
            this.mc.player.setXRot(-90.0F);
         }

         switch (this.step) {
            case START:
               PlayerUtils.centerPlayer();
               this.mc.player.setDeltaMovement(0.0, this.mc.player.getDeltaMovement().y, 0.0);
               int blocked = this.blockedAbove();
               if (blocked != -2147483648) {
                  this.giveUp(
                     String.format("Not enough room above to throw a pearl: blocked at Y %d (it needs open blocks up to Y %d).", blocked, this.flightTop())
                  );
                  return;
               }

               this.startPitch = this.mc.player.getXRot();
               this.next(PearlMethod.Step.LOOK_UP);
               break;
            case LOOK_UP:
               double t = Math.min(1.0, (System.nanoTime() - this.stepStart) / 5.0E8);
               double eased = t * t * (3.0 - 2.0 * t);
               this.mc.player.setXRot((float)(this.startPitch + (-90.0 - this.startPitch) * eased));
               if (t >= 1.0) {
                  this.next(PearlMethod.Step.HOLD_SELECT);
               }
               break;
            case HOLD_SELECT:
               if (!this.elapsed(500000000L)) {
                  return;
               }

               FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
               if (!pearl.found()) {
                  this.giveUp("You have no ender pearls on your hotbar.");
                  return;
               }

               if (!InvUtils.swap(pearl.slot(), false)) {
                  this.giveUp("Couldn't switch to your ender pearl.");
                  return;
               }

               this.next(PearlMethod.Step.HOLD_THROW);
               break;
            case HOLD_THROW:
               if (!this.elapsed(500000000L)) {
                  return;
               }

               if (this.mc.gameMode.isDestroying()) {
                  return;
               }

               if (!this.mc.player.getMainHandItem().is(Items.ENDER_PEARL)) {
                  this.giveUp("Your ender pearl left your hand before the throw.");
                  return;
               }

               InteractionResult result = this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
               if (!result.consumesAction()) {
                  this.giveUp("The pearl didn't throw - it may still be on cooldown.");
                  return;
               }

               this.mc.player.swing(InteractionHand.MAIN_HAND, this.mc.player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
               this.next(PearlMethod.Step.RTP);
               break;
            case RTP:
               if (!this.elapsed(100000000L)) {
                  return;
               }

               this.mc.getConnection().sendCommand("rtp");
               this.done();
         }
      }
   }

   private int blockedAbove() {
      BlockPos feet = this.mc.player.blockPosition();
      MutableBlockPos pos = new MutableBlockPos();

      for (int y = feet.getY() + 2; y <= this.flightTop(); y++) {
         pos.set(feet.getX(), y, feet.getZ());
         if (!this.mc.level.getBlockState(pos).getCollisionShape(this.mc.level, pos).isEmpty()) {
            return y;
         }
      }

      return -2147483648;
   }

   private int flightTop() {
      return (int)Math.floor(this.mc.player.getEyeY() - 0.1 + 29.45);
   }

   private void giveUp(String why) {
      this.error("%s", new Object[]{why});
      this.done();
   }

   private void done() {
      this.step = PearlMethod.Step.IDLE;
      this.trigger.finished();
      KeyMapping.setAll();
   }

   private void next(PearlMethod.Step to) {
      this.step = to;
      this.stepStart = System.nanoTime();
   }

   private boolean elapsed(long nanos) {
      return System.nanoTime() - this.stepStart >= nanos;
   }

   private static enum Step {
      IDLE,
      START,
      LOOK_UP,
      HOLD_SELECT,
      HOLD_THROW,
      RTP;
   }
}
