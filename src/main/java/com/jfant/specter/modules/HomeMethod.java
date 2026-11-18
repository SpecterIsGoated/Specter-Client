package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.game.ReceiveMessageEvent;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.IntSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.util.Trigger;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;

public class HomeMethod extends Module {
   private static final int HOME_DELAY_TICKS = 5;
   private static final int RTP_DELAY_TICKS = 10;
   private static final double RTP_MOVED_SQ = 64.0;
   private static final int RTP_TIMEOUT_TICKS = 600;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Trigger trigger = new Trigger(this.sgGeneral, "saves a home, runs /rtp and goes back home", -1);
   private final Setting<Integer> homeSlot = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("home-slot")
            .description("Which home slot to overwrite and return to.")
            .defaultValue(3)
            .range(1, 3)
            .sliderRange(1, 3)
            .build()
      );
   private HomeMethod.Step step = HomeMethod.Step.IDLE;
   private int wait;
   private int rtpTicks;
   private double startX;
   private double startY;
   private double startZ;
   private boolean rtpDone;

   public HomeMethod() {
      super(
         SpecterClientAddon.TOOLS,
         "home-method",
         "Saves a home, runs /rtp, then returns home - a relog without disconnecting, since the server reloads every chunk around your home when you come back. Runs on a key press or when you go down to a height."
      );
   }

   @Override
   public void onActivate() {
      this.step = HomeMethod.Step.IDLE;
      this.wait = 0;
      this.rtpTicks = 0;
      this.rtpDone = false;
      this.trigger.reset();
      this.info("%s", new Object[]{this.trigger.waitingLine()});
   }

   @Override
   public void onDeactivate() {
      this.step = HomeMethod.Step.IDLE;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null) {
         switch (this.step) {
            case IDLE:
               if (this.trigger.poll(this.mc)) {
                  this.step = HomeMethod.Step.DEL_HOME;
               }
               break;
            case DEL_HOME:
               this.command("delhome " + this.homeSlot.get());
               this.wait = 5;
               this.step = HomeMethod.Step.SET_HOME;
               break;
            case SET_HOME:
               if (!this.waited()) {
                  return;
               }

               this.command("sethome " + this.homeSlot.get());
               this.wait = 10;
               this.step = HomeMethod.Step.RTP;
               break;
            case RTP:
               if (!this.waited()) {
                  return;
               }

               this.command("rtp");
               this.startX = this.mc.player.getX();
               this.startY = this.mc.player.getY();
               this.startZ = this.mc.player.getZ();
               this.rtpTicks = 0;
               this.rtpDone = false;
               this.step = HomeMethod.Step.WAIT_RTP;
               break;
            case WAIT_RTP:
               if (++this.rtpTicks >= 600 || this.rtpDone || this.movedFar()) {
                  this.wait = 5;
                  this.step = HomeMethod.Step.HOME;
               }
               break;
            case HOME:
               if (!this.waited()) {
                  return;
               }

               this.command("home " + this.homeSlot.get());
               this.step = HomeMethod.Step.IDLE;
               this.trigger.finished();
         }
      }
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      if (this.step == HomeMethod.Step.WAIT_RTP) {
         String text = event.getMessage().getString().toLowerCase(Locale.ROOT);
         if (text.contains("random location") || text.contains("teleported")) {
            this.rtpDone = true;
         }
      }
   }

   private boolean movedFar() {
      double dx = this.mc.player.getX() - this.startX;
      double dy = this.mc.player.getY() - this.startY;
      double dz = this.mc.player.getZ() - this.startZ;
      return dx * dx + dy * dy + dz * dz >= 64.0;
   }

   private boolean waited() {
      if (this.wait > 0) {
         this.wait--;
         return false;
      } else {
         return true;
      }
   }

   private void command(String text) {
      if (this.mc.getConnection() != null) {
         this.mc.getConnection().sendCommand(text);
      }
   }

   private static enum Step {
      IDLE,
      DEL_HOME,
      SET_HOME,
      RTP,
      WAIT_RTP,
      HOME;
   }
}
