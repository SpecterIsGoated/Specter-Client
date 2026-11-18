package com.jfant.specter.auto.inv;

import com.jfant.specter.core.SpecterCore;
import com.jfant.specter.core.events.game.GameLeftEvent;
import com.jfant.specter.core.events.packets.PacketEvent;
import com.jfant.specter.pvp.Hands;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

public final class Latency {
   private static final long FRESH_NANOS = 30000000000L;
   private static final int DEFAULT_MS = 150;
   private static final int MAX_TRACKED = 256;
   private static final ConcurrentHashMap<Integer, Long> sentAt = new ConcurrentHashMap<>();
   private static volatile double smoothedMs = -1.0;
   private static volatile long sampledAt;
   private static boolean started;

   private Latency() {
   }

   static void init() {
      if (!started) {
         started = true;
         SpecterCore.EVENT_BUS.subscribe(new Latency.Listener());
      }
   }

   public static int rttMs() {
      double measured = smoothedMs;
      if (measured >= 0.0 && System.nanoTime() - sampledAt < 30000000000L) {
         return (int)Math.round(measured);
      } else {
         int ping = Hands.pingMs();
         return ping > 0 ? ping : 150;
      }
   }

   public static long confirmWindowMs() {
      return Math.max(250L, Math.min(3000L, rttMs() * 2L + 150L));
   }

   static void sample(double ms) {
      if (!(ms < 0.0) && !(ms > 5000.0)) {
         double old = smoothedMs;
         smoothedMs = old < 0.0 ? ms : old * 0.75 + ms * 0.25;
         sampledAt = System.nanoTime();
      }
   }

   private static final class Listener {
      @EventHandler
      private void onSend(PacketEvent.Send event) {
         int sequence;
         if (event.packet instanceof ServerboundPlayerActionPacket packet) {
            sequence = packet.getSequence();
         } else if (event.packet instanceof ServerboundUseItemPacket packet) {
            sequence = packet.sequence();
         } else {
            if (!(event.packet instanceof ServerboundUseItemOnPacket packet)) {
               return;
            }

            sequence = packet.sequence();
         }

         if (sequence > 0) {
            if (Latency.sentAt.size() > 256) {
               Latency.sentAt.clear();
            }

            Latency.sentAt.put(sequence, System.nanoTime());
         }
      }

      @EventHandler
      private void onReceive(PacketEvent.Receive event) {
         if (event.packet instanceof ClientboundBlockChangedAckPacket packet) {
            long var6 = System.nanoTime();
            Long sent = Latency.sentAt.remove(packet.sequence());
            Latency.sentAt.keySet().removeIf(sequence -> sequence <= packet.sequence());
            if (sent != null) {
               Latency.sample((var6 - sent) / 1000000.0);
            }
         }
      }

      @EventHandler
      private void onGameLeft(GameLeftEvent event) {
         Latency.sentAt.clear();
         Latency.smoothedMs = -1.0;
         Latency.sampledAt = 0L;
      }
   }
}
