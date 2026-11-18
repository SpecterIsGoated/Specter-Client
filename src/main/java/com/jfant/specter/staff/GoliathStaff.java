package com.jfant.specter.staff;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.IntPredicate;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;

public final class GoliathStaff {
   private static final long LANDING_MS = 4000L;
   private static final long DELAY_MS = 500L;
   private static final long ROTATION_MS = 300L;
   private static final int BURST = 5;
   private static final long LOGOFF_MS = 15000L;
   private static final long TAB_KEEP_MS = 5000L;
   private static final EnumSet<Action> REFRESH = EnumSet.of(Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LIST_ORDER);
   private final ConcurrentLinkedQueue<GoliathStaff.Ev> inbox = new ConcurrentLinkedQueue<>();
   private final Map<UUID, String> names = new ConcurrentHashMap<>();
   private final List<GoliathStaff.Ev> pending = new ArrayList<>();
   private final ArrayDeque<GoliathStaff.Ev> recentTab = new ArrayDeque<>();
   private final Map<Long, Integer> teamBurst = new HashMap<>();
   private final Map<String, GoliathStaff.Here> staff = new HashMap<>();
   private long landedAt;

   public void onPacket(Packet<?> packet) {
      long now = System.currentTimeMillis();
      if (packet instanceof ClientboundBundlePacket bundle) {
         for (Packet<?> inner : bundle.subPackets()) {
            this.onPacket(inner);
         }
      } else if (packet instanceof ClientboundLoginPacket || packet instanceof ClientboundRespawnPacket) {
         this.inbox.add(new GoliathStaff.Ev(GoliathStaff.Kind.LANDED, null, null, -1, now));
      } else if (packet instanceof ClientboundPlayerInfoUpdatePacket list) {
         EnumSet<Action> actions = list.actions();
         GoliathStaff.Kind kind;
         if (actions.contains(Action.ADD_PLAYER)) {
            kind = GoliathStaff.Kind.TAB_ADD;
         } else {
            if (!actions.equals(REFRESH)) {
               return;
            }

            kind = GoliathStaff.Kind.REFRESH;
         }

         for (Entry e : list.entries()) {
            String name = e.profile() != null ? e.profile().name() : null;
            if (name != null && e.profileId() != null) {
               this.names.put(e.profileId(), name);
            }

            this.inbox.add(new GoliathStaff.Ev(kind, e.profileId(), name, -1, now));
         }
      } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
         for (UUID id : remove.profileIds()) {
            this.inbox.add(new GoliathStaff.Ev(GoliathStaff.Kind.TAB_REMOVE, id, this.names.get(id), -1, now));
         }
      } else if (packet instanceof ClientboundSetPlayerTeamPacket team) {
         net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action op = team.getTeamAction();
         if (op == null) {
            return;
         }

         String[] parsed = rankTeam(team.getName());
         if (parsed == null) {
            return;
         }

         int order = Integer.parseInt(parsed[0]);
         GoliathStaff.Kind kind = op == net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Action.ADD
            ? GoliathStaff.Kind.TEAM_ADD
            : GoliathStaff.Kind.TEAM_REMOVE;
         this.inbox.add(new GoliathStaff.Ev(kind, null, parsed[1], order, now));
      }
   }

   static String[] rankTeam(String team) {
      if (team == null || team.length() < 4 || team.charAt(2) != '-') {
         return null;
      } else if (Character.isDigit(team.charAt(0)) && Character.isDigit(team.charAt(1))) {
         int end = team.lastIndexOf(45);
         if (end <= 3) {
            return null;
         } else {
            String flag = team.substring(end + 1);
            return !flag.equals("false") && !flag.equals("true") ? null : new String[]{team.substring(0, 2), team.substring(3, end)};
         }
      } else {
         return null;
      }
   }

   public void reset() {
      this.inbox.clear();
      this.pending.clear();
      this.recentTab.clear();
      this.teamBurst.clear();
      this.staff.clear();
      this.names.clear();
      this.landedAt = System.currentTimeMillis();
   }

   public List<GoliathStaff.Change> poll(long now, ClientPacketListener handler, String self, Function<String, Integer> staffOrder, IntPredicate watched) {
      List<GoliathStaff.Change> out = new ArrayList<>();

      GoliathStaff.Ev ev;
      while ((ev = this.inbox.poll()) != null) {
         switch (ev.kind()) {
            case LANDED:
               this.landedAt = Math.max(this.landedAt, ev.at());
               break;
            case TAB_ADD:
            case TAB_REMOVE:
               this.recentTab.addLast(ev);
               break;
            case REFRESH:
               this.pending.add(ev);
               break;
            case TEAM_ADD:
            case TEAM_REMOVE:
               this.teamBurst.merge(ev.at(), 1, Integer::sum);
               this.pending.add(ev);
         }
      }

      String selfKey = self.toLowerCase(Locale.ROOT);
      Iterator<GoliathStaff.Ev> it = this.pending.iterator();

      while (it.hasNext()) {
         GoliathStaff.Ev e = it.next();
         if (now - e.at() >= 500L) {
            it.remove();
            if (e.at() - this.landedAt >= 4000L) {
               if (e.kind() == GoliathStaff.Kind.REFRESH) {
                  PlayerInfo entry = e.id() != null ? handler.getPlayerInfo(e.id()) : null;
                  String name = entry != null ? entry.getProfile().name() : this.names.get(e.id());
                  if (name != null && !name.equalsIgnoreCase(selfKey)) {
                     int order = staffOrder.apply(name.toLowerCase(Locale.ROOT));
                     if (order >= 0 && watched.test(order)) {
                        this.arrive(name, GoliathStaff.How.REFRESH, e.at(), handler, out);
                     }
                  }
               } else if (e.order() >= 0
                  && e.order() <= 8
                  && !e.name().equalsIgnoreCase(selfKey)
                  && this.teamBurst.getOrDefault(e.at(), 0) < 5
                  && !this.rotated(e)
                  && watched.test(e.order())) {
                  if (e.kind() == GoliathStaff.Kind.TEAM_ADD) {
                     this.arrive(e.name(), GoliathStaff.How.TEAM, e.at(), handler, out);
                  } else {
                     this.leave(e.name(), e.at(), false, handler, out);
                  }
               }
            }
         }
      }

      for (GoliathStaff.Here h : this.staff.values()) {
         if (h.here()) {
            if (handler.getPlayerInfo(h.name) != null) {
               h.lastOnTab = now;
            } else if (h.lastOnTab != 0L && now - h.lastOnTab > 15000L) {
               this.leave(h.name, now, true, handler, out);
            }
         }
      }

      while (!this.recentTab.isEmpty() && now - this.recentTab.peekFirst().at() > 5000L) {
         this.recentTab.pollFirst();
      }

      this.teamBurst.keySet().removeIf(at -> now - at > 5000L);
      return out;
   }

   private boolean rotated(GoliathStaff.Ev team) {
      for (GoliathStaff.Ev tab : this.recentTab) {
         if (Math.abs(tab.at() - team.at()) <= 300L) {
            String n = tab.name() != null ? tab.name() : this.names.get(tab.id());
            if (n == null || n.equalsIgnoreCase(team.name())) {
               return true;
            }
         }
      }

      return false;
   }

   private void arrive(String name, GoliathStaff.How how, long at, ClientPacketListener handler, List<GoliathStaff.Change> out) {
      GoliathStaff.Here h = this.staff.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new GoliathStaff.Here(name));
      boolean onTab = handler.getPlayerInfo(name) != null;
      if (onTab) {
         h.lastOnTab = at;
      }

      if (!h.here() || h.since == 0L) {
         h.how = how;
         h.since = at;
         h.leftAt = 0L;
         h.loggedOff = false;
         out.add(new GoliathStaff.Change(h.name, true, how, false, onTab));
      }
   }

   private void leave(String name, long at, boolean loggedOff, ClientPacketListener handler, List<GoliathStaff.Change> out) {
      GoliathStaff.Here h = this.staff.get(name.toLowerCase(Locale.ROOT));
      if (h != null && h.here()) {
         h.leftAt = at;
         h.loggedOff = loggedOff;
         out.add(new GoliathStaff.Change(h.name, false, h.how, loggedOff, handler.getPlayerInfo(name) != null));
      }
   }

   public List<GoliathStaff.Here> list(long now, long keepMs) {
      List<GoliathStaff.Here> shown = new ArrayList<>();
      Iterator<GoliathStaff.Here> it = this.staff.values().iterator();

      while (it.hasNext()) {
         GoliathStaff.Here h = it.next();
         if (h.here()) {
            shown.add(h);
         } else if (now - h.leftAt <= keepMs) {
            shown.add(h);
         } else {
            it.remove();
         }
      }

      shown.sort((a, b) -> a.here() != b.here() ? (a.here() ? -1 : 1) : Long.compare(b.since, a.since));
      return shown;
   }

   public boolean here(String key) {
      GoliathStaff.Here h = this.staff.get(key);
      return h != null && h.here();
   }

   public record Change(String name, boolean arrived, GoliathStaff.How how, boolean loggedOff, boolean onTab) {
   }

   private record Ev(GoliathStaff.Kind kind, UUID id, String name, int order, long at) {
   }

   public static final class Here {
      public final String name;
      public GoliathStaff.How how;
      public long since;
      public long leftAt;
      public long lastOnTab;
      public boolean loggedOff;

      Here(String name) {
         this.name = name;
      }

      public boolean here() {
         return this.leftAt == 0L;
      }
   }

   public static enum How {
      TEAM("came onto your goliath"),
      REFRESH("came onto your goliath (tab refresh)");

      public final String text;

      private How(String text) {
         this.text = text;
      }
   }

   private static enum Kind {
      LANDED,
      TAB_ADD,
      TAB_REMOVE,
      REFRESH,
      TEAM_ADD,
      TEAM_REMOVE;
   }
}
