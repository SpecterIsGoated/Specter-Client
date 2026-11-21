package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.entity.player.AttackEntityEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.Melee;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import com.jfant.specter.pvp.Targets;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;

public class ShieldBreaker extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("switch-back").description("Go back to your slot on the tick after the hit.").defaultValue(true).build());
   private final Setting<Boolean> debug = this.sgDebug
      .add(new BoolSetting.Builder().name("debug").description("Say in chat (only you see it) each swap.").defaultValue(false).build());
   private final SlotReturn slots = new SlotReturn();
   private boolean returnNext;

   public ShieldBreaker() {
      super(
         SpecterClientAddon.PVP,
         "shield-breaker",
         "Hitting a player whose shield faces you swaps the hit to an axe from your hotbar, which disables the shield, then swaps back."
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

   public static boolean wants(LivingEntity target) {
      ShieldBreaker module = Modules.get().get(ShieldBreaker.class);
      return module != null
         && module.isActive()
         && module.mc.player != null
         && Targets.enemy(target)
         && Melee.shieldFacing(target, module.mc.player)
         && Hands.hotbarSlot(stack -> stack.is(ItemTags.AXES)) != -1;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.player != null && event.entity instanceof LivingEntity target && wants(target)) {
         if (!this.mc.player.getMainHandItem().is(ItemTags.AXES) && PvpLock.acquire(this)) {
            int axe = Hands.hotbarSlot(stack -> stack.is(ItemTags.AXES));
            this.slots.take(axe);
            this.returnNext = true;
            if (this.debug.get()) {
               this.info("Shield up on %s: hit with the axe in slot %d.", new Object[]{target.getName().getString(), axe + 1});
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
