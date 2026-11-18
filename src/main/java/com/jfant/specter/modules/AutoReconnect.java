package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.world.ServerConnectBeginEvent;
import com.jfant.specter.core.settings.BoolSetting;
import com.jfant.specter.core.settings.DoubleSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;

public class AutoReconnect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> delay = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("delay")
            .description("Seconds to wait on the disconnect screen before joining again.")
            .defaultValue(3.5)
            .min(0.0)
            .sliderRange(0.0, 30.0)
            .decimalPlaces(1)
            .build()
      );
   private final Setting<Boolean> hideButtons = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("hide-buttons")
            .description("Leaves the Reconnect and Toggle Auto Reconnect buttons off the disconnect screen.")
            .defaultValue(false)
            .build()
      );
   @Nullable
   private ServerAddress lastAddress;
   @Nullable
   private ServerData lastInfo;

   public AutoReconnect() {
      super(
         SpecterClientAddon.TOOLS,
         "auto-reconnect",
         "Joins the last server again after a disconnect, once the delay has counted down on the disconnect screen."
      );
      SpecterCore.EVENT_BUS.subscribe(new AutoReconnect.Remember());
   }

   public boolean hasServer() {
      return this.lastAddress != null;
   }

   public double delayTicks() {
      return this.delay.get() * 20.0;
   }

   public boolean buttonsHidden() {
      return this.hideButtons.get();
   }

   public void reconnect() {
      if (this.lastAddress != null && this.lastInfo != null) {
         ConnectScreen.startConnecting(new TitleScreen(), this.mc, this.lastAddress, this.lastInfo, false, null);
      }
   }

   private final class Remember {
      @EventHandler
      private void onConnectBegin(ServerConnectBeginEvent event) {
         AutoReconnect.this.lastAddress = event.address;
         AutoReconnect.this.lastInfo = event.info;
      }
   }
}
