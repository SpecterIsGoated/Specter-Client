package com.jfant.specter.util;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameJoinedEvent;
import com.jfant.specter.core.events.game.OpenScreenEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.core.systems.modules.Modules;
import com.jfant.specter.core.utils.player.ChatUtils;
import com.jfant.specter.modules.AutoReconnect;
import java.util.Locale;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;

public final class Leaving {
   private static final long FRESH_MS = 15000L;
   private static final String[] BAN_WORDS = new String[]{"banned", "ban ", "blacklist", "suspended"};
   private static final Leaving.Listener LISTENER = new Leaving.Listener();
   private static boolean subscribed;
   private static volatile String purposeBy;
   private static volatile boolean banned;
   private static volatile long markedAt;
   private static boolean switchedOff;

   private Leaving() {
   }

   public static void init() {
      if (!subscribed) {
         subscribed = true;
         SpecterCore.EVENT_BUS.subscribe(LISTENER);
      }
   }

   public static void onPurpose(String who) {
      purposeBy = who;
      markedAt = System.currentTimeMillis();
   }

   public static String reason() {
      if (System.currentTimeMillis() - markedAt > 15000L) {
         return null;
      } else if (banned) {
         return "you were banned";
      } else {
         return purposeBy == null ? null : purposeBy + " left on purpose";
      }
   }

   public static boolean looksLikeBan(String reason) {
      if (reason == null) {
         return false;
      } else {
         String text = reason.toLowerCase(Locale.ROOT);

         for (String word : BAN_WORDS) {
            if (text.contains(word)) {
               return true;
            }
         }

         return false;
      }
   }

   private static void clear() {
      purposeBy = null;
      banned = false;
      markedAt = 0L;
   }

   private static void sawKick(Component reason) {
      if (reason != null && looksLikeBan(reason.getString())) {
         banned = true;
         markedAt = System.currentTimeMillis();
      }
   }

   private static final class Listener {
      @EventHandler
      private void onPacket(PacketEvent.Receive event) {
         if (event.packet instanceof ClientboundDisconnectPacket kick) {
            Leaving.sawKick(kick.reason());
         } else if (event.packet instanceof ClientboundLoginDisconnectPacket kick) {
            Leaving.sawKick(kick.reason());
         }
      }

      @EventHandler
      private void onOpenScreen(OpenScreenEvent event) {
         if (event.screen instanceof DisconnectedScreen) {
            String why = Leaving.reason();
            if (why != null) {
               AutoReconnect reconnect = Modules.get().get(AutoReconnect.class);
               if (reconnect != null && reconnect.isActive()) {
                  reconnect.toggle();
                  Leaving.switchedOff = true;
               }

               SpecterClientAddon.LOG.info("Not reconnecting: {}", why);
               ChatUtils.warning("Not reconnecting: %s.", why);
            }
         }
      }

      @EventHandler
      private void onGameJoined(GameJoinedEvent event) {
         Leaving.clear();
         if (Leaving.switchedOff) {
            Leaving.switchedOff = false;
            AutoReconnect reconnect = Modules.get().get(AutoReconnect.class);
            if (reconnect != null && !reconnect.isActive()) {
               reconnect.toggle();
            }
         }
      }
   }
}
