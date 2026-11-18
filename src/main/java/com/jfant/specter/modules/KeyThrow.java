package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.KeybindSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.core.utils.misc.Keybind;
import com.jfant.specter.pvp.Hands;
import com.jfant.specter.pvp.PvpLock;
import com.jfant.specter.pvp.SlotReturn;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class KeyThrow extends Module {
   private static final int PRESS_TICKS = 10;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDebug = this.settings.createGroup("Debug");
   private final Setting<Keybind> pearlKey = this.sgGeneral
      .add(new KeybindSetting.Builder().name("pearl-key").description("Throws an ender pearl where you look.").defaultValue(Keybind.none()).build());
   private final Setting<Keybind> windKey = this.sgGeneral
      .add(new KeybindSetting.Builder().name("wind-charge-key").description("Throws a wind charge where you look.").defaultValue(Keybind.none()).build());
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(new BoolSetting.Builder().name("switch-back").description("Go back to your slot after the throw.").defaultValue(true).build());
   private final Setting<Boolean> debug = this.sgDebug
      .add(
         new BoolSetting.Builder()
            .name("debug")
            .description("Say in chat (only you see it) each throw, or why a press was dropped.")
            .defaultValue(false)
            .build()
      );
   private final SlotReturn slots = new SlotReturn();
   private boolean pearlDown;
   private boolean windDown;
   private Item pending;
   private int pendingTicks;
   private boolean returnNext;

   public KeyThrow() {
      super(
         SpecterClientAddon.PVP,
         "key-throw",
         "A key of its own throws an ender pearl, another a wind charge, from your hotbar or offhand, then puts you back on your slot."
      );
   }

   @Override
   public void onDeactivate() {
      this.finish();
      this.pending = null;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.slots.forget();
      this.pending = null;
      this.returnNext = false;
      PvpLock.release(this);
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.returnNext) {
         this.returnNext = false;
         this.finish();
      }

      boolean pearl = this.mc.gui.screen() == null && this.pearlKey.get().isSet() && this.pearlKey.get().isPressed();
      boolean wind = this.mc.gui.screen() == null && this.windKey.get().isSet() && this.windKey.get().isPressed();
      if (pearl && !this.pearlDown) {
         this.press(Items.ENDER_PEARL);
      }

      if (wind && !this.windDown) {
         this.press(Items.WIND_CHARGE);
      }

      this.pearlDown = pearl;
      this.windDown = wind;
      if (this.pending != null) {
         if (--this.pendingTicks < 0) {
            if (this.debug.get()) {
               this.info("Dropped the %s press: the hand stayed busy.", new Object[]{this.pending.getName().getString()});
            }

            this.pending = null;
            this.finish();
         } else {
            LocalPlayer player = this.mc.player;
            if (player != null && Hands.canAct() && !PvpLock.actedThisTick() && PvpLock.acquire(this)) {
               InteractionHand hand;
               if (player.getMainHandItem().is(this.pending)) {
                  hand = InteractionHand.MAIN_HAND;
               } else {
                  if (!player.getOffhandItem().is(this.pending) || !Hands.passesClick(player.getMainHandItem())) {
                     int slot = Hands.hotbarSlot(this.pending);
                     if (slot == -1) {
                        if (this.debug.get()) {
                           this.info("No %s in the hotbar.", new Object[]{this.pending.getName().getString()});
                        }

                        this.pending = null;
                        PvpLock.release(this);
                        return;
                     }

                     this.slots.take(slot);
                     return;
                  }

                  hand = InteractionHand.OFF_HAND;
               }

               ItemStack stack = player.getItemInHand(hand);
               if (!Hands.coolingDown(stack)) {
                  if (Hands.use(hand)) {
                     if (this.debug.get()) {
                        this.info("Threw a %s.", new Object[]{this.pending.getName().getString()});
                     }

                     this.pending = null;
                     this.returnNext = true;
                  }
               }
            }
         }
      }
   }

   private void press(Item item) {
      this.pending = item;
      this.pendingTicks = 10;
   }

   private void finish() {
      this.slots.restore(this.switchBack.get());
      PvpLock.release(this);
   }
}
