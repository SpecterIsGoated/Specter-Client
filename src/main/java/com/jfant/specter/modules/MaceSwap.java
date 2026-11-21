package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import com.jfant.specter.pvp.Targets;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public class MaceSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Double> minFall = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("min-fall")
            .description("Blocks fallen before a hit is swapped to the mace (a smash needs more than 1.5).")
            .defaultValue(3.0)
            .range(1.6, 30.0)
            .sliderRange(1.6, 30.0)
            .build()
      );
   private final Setting<Boolean> mobs = this.sgGeneral
      .add(new BoolSetting.Builder().name("mobs").description("Swap for hits on mobs too, not only players.").defaultValue(false).build());
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("switch-back").description("Go back to your slot on the tick after the hit.").defaultValue(true).build());
   private final Setting<Boolean> debug = this.sgDebug
      .add(new BoolSetting.Builder().name("debug").description("Say in chat (only you see it) each swap and the fall it was for.").defaultValue(false).build());
   private final SlotReturn slots = new SlotReturn();
   private boolean returnNext;

   public MaceSwap() {
      super(
         SpecterClientAddon.PVP,
         "mace-swap",
         "Hitting something while falling far enough for a mace smash swaps the hit to the mace in your hotbar, then swaps back."
      );
   }

   @Override
   public void onActivate() {
      Module attributeSwap = Modules.get().get("attribute-swap");
      if (attributeSwap != null && attributeSwap.isActive()) {
         this.warning("Attribute Swap is on as well and also swaps on hits.", new Object[0]);
      }
   }

   @Override
   public void onDeactivate() {
      this.slots.restore(this.switchBack.get());
      PvpLock.release(this);
      this.returnNext = false;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      LocalPlayer player = this.mc.player;
      if (player != null && event.entity instanceof LivingEntity target) {
         if (this.mobs.get() || Targets.enemy(target)) {
            if (!(player.fallDistance < this.minFall.get()) && !player.isFallFlying() && !player.onGround()) {
               if (!player.getMainHandItem().is(Items.MACE) && !ShieldBreaker.wants(target)) {
                  int mace = Hands.hotbarSlot(Items.MACE);
                  if (mace != -1 && PvpLock.acquire(this)) {
                     this.slots.take(mace);
                     this.returnNext = true;
                     if (this.debug.get()) {
                        this.info(
                           "Fell %.1f blocks: hit %s with the mace in slot %d.", new Object[]{player.fallDistance, target.getName().getString(), mace + 1}
                        );
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.returnNext) {
         this.returnNext = false;
         this.slots.restore(this.switchBack.get());
         PvpLock.release(this);
      }
   }
}
