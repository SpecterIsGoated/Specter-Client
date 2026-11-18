package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.util.Trigger;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;

public class AutoRelog extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Trigger trigger = new Trigger(this.sgGeneral, "disconnects and reconnects to the same server", -1);
   private ServerData pending;

   public AutoRelog() {
      super(
         SpecterClientAddon.TOOLS, "auto-relog", "Disconnects and immediately reconnects to the same server - on a key press or when you go down to a height."
      );
      this.runInMainMenu = true;
   }

   @Override
   public void onActivate() {
      this.pending = null;
      this.trigger.reset();
      this.info("%s", new Object[]{this.trigger.waitingLine()});
   }

   @Override
   public void onDeactivate() {
      this.pending = null;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.pending != null) {
         this.reconnectIfReady();
      } else if (this.mc.player != null && this.mc.level != null && this.mc.getConnection() != null) {
         if (this.trigger.poll(this.mc)) {
            ServerData server = this.mc.getCurrentServer();
            if (server != null && server.ip != null && !server.ip.isBlank()) {
               this.pending = server;
               String why = this.trigger.mode() == Trigger.Mode.BelowY ? "reached Y " + this.trigger.y() : "key pressed";
               this.mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(Component.literal("Auto relog: " + why)));
               this.reconnectIfReady();
            } else {
               this.error("Cannot relog: no multiplayer server address is available.", new Object[0]);
               this.trigger.finished();
            }
         }
      }
   }

   private void reconnectIfReady() {
      if (this.pending != null && this.mc.player == null && this.mc.level == null) {
         ServerData server = this.pending;
         this.pending = null;
         this.trigger.finished();
         ConnectScreen.startConnecting(new TitleScreen(), this.mc, ServerAddress.parseString(server.ip), server, false, null);
      }
   }
}
