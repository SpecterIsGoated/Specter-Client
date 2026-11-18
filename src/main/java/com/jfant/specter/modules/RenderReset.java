package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.util.Trigger;
import meteordevelopment.orbit.EventHandler;

public class RenderReset extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Trigger trigger = new Trigger(this.sgGeneral, "reloads the chunks", -1);
   private final Setting<Integer> lowDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("low-distance")
            .description("Render distance to drop to. 2 is the vanilla minimum and unloads the most.")
            .defaultValue(2)
            .min(2)
            .sliderRange(2, 8)
            .build()
      );
   private final Setting<Boolean> useCurrent = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("use-current")
            .description("Go back to the render distance you had when it started, instead of a fixed value.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Integer> restoreDistance = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("restore-distance")
            .description("Render distance to come back up to.")
            .defaultValue(32)
            .min(2)
            .sliderRange(2, 32)
            .visible(() -> !this.useCurrent.get())
            .build()
      );
   private final Setting<Integer> holdTicks = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("hold-ticks")
            .description(
               "How long to stay at the low distance before coming back up. Too short and the server has not finished unloading, so it re-sends nothing."
            )
            .defaultValue(20)
            .min(1)
            .sliderRange(5, 100)
            .build()
      );
   private final Setting<Boolean> notify = this.sgGeneral
      .add(new BoolSetting.Builder().name("notify").description("Print a line in chat when the cycle finishes.").defaultValue(true).build());
   private boolean dropped;
   private int restoreTo;
   private int ticks;

   public RenderReset() {
      super(
         SpecterClientAddon.TOOLS,
         "render-reset",
         "Drops render distance to the minimum and back up, forcing the server to re-send every chunk around you - on a key press or when you go down to a height."
      );
   }

   @Override
   public void onActivate() {
      this.trigger.reset();
      this.info("%s", new Object[]{this.trigger.waitingLine()});
   }

   private void startCycle() {
      int current = this.mc.options.renderDistance().get();
      this.restoreTo = this.useCurrent.get() ? current : this.restoreDistance.get();
      if (this.restoreTo <= this.lowDistance.get()) {
         this.warning("restore-distance (%d) is not above low-distance (%d) - nothing will be re-sent.", new Object[]{this.restoreTo, this.lowDistance.get()});
      }

      this.ticks = 0;
      this.dropped = true;
      this.apply(this.lowDistance.get());
   }

   @Override
   public void onDeactivate() {
      this.restore();
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!this.dropped) {
         if (this.mc.level != null && this.mc.getConnection() != null && this.trigger.poll(this.mc)) {
            if (this.notify.get() && this.trigger.mode() == Trigger.Mode.BelowY) {
               this.info("Down to Y %d - reloading chunks.", new Object[]{this.trigger.y()});
            }

            this.startCycle();
         }
      } else if (++this.ticks >= this.holdTicks.get()) {
         int from = this.lowDistance.get();
         this.restore();
         if (this.notify.get()) {
            this.info("Reloaded chunks: %d -> %d.", new Object[]{from, this.restoreTo});
         }

         this.trigger.finished();
      }
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent event) {
      this.restore();
   }

   private void restore() {
      if (this.dropped) {
         this.dropped = false;
         this.apply(this.restoreTo);
         this.mc.options.save();
      }
   }

   private void apply(int distance) {
      this.mc.options.renderDistance().set(distance);
      if (this.mc.getConnection() != null) {
         this.mc.options.broadcastOptions();
      }
   }
}
