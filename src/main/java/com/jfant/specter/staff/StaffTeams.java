package com.jfant.specter.staff;

import com.jfant.specter.core.SpecterCore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.scores.PlayerTeam;

public final class StaffTeams {
   public static final int LAST_ORDER = 8;
   public static final int DEVELOPER_LAST = 2;
   public static final Map<String, Integer> KNOWN = Map.ofEntries(
      Map.entry("drdonutt", 0),
      Map.entry("owen1212055", 2),
      Map.entry("archivepedro", 2),
      Map.entry("fluffymaster07", 3),
      Map.entry("w1zox_", 3),
      Map.entry("munkerlich", 5),
      Map.entry("pastagamer08", 5),
      Map.entry("bloodspulse", 5),
      Map.entry("0gsummer", 6),
      Map.entry("fallerfly", 7),
      Map.entry("auzzitech", 8),
      Map.entry("captainmoose35", 8),
      Map.entry("frenk_btw", 8),
      Map.entry("napooo_", 8),
      Map.entry("zeef69", 8),
      Map.entry("u_vv", 8),
      Map.entry("cryptodaveyt", 8),
      Map.entry("showered", 8),
      Map.entry("bobisfound", 8),
      Map.entry("noahvdaa", 8),
      Map.entry("lzouzmp5", 8),
      Map.entry("splaterd", 8),
      Map.entry("_chaon", 8),
      Map.entry("dough4", 8),
      Map.entry("evify", 8)
   );
   public static final int LIST_ORDER_TOP = 100000;
   private static final Map<String, Integer> LEARNED = new ConcurrentHashMap<>();
   private static final File LEARNED_FILE = new File(SpecterCore.FOLDER, "staff-learned.txt");
   private static volatile boolean learnedDirty;
   private static final Set<String> UNRANKED;

   public static int listOrderRank(PlayerInfo entry) {
      if (entry == null) {
         return -1;
      } else {
         int rank = 100000 - entry.getTabListOrder();
         return rank >= 0 && rank <= 9 ? rank : -1;
      }
   }

   private StaffTeams() {
   }

   public static Map<String, StaffTeams.Member> scan(ClientLevel world, ClientPacketListener handler, String selfName, Map<String, StaffTeams.Member> out) {
      out.clear();
      Map<String, PlayerInfo> entries = new HashMap<>();

      for (PlayerInfo entry : handler.getOnlinePlayers()) {
         entries.put(entry.getProfile().name().toLowerCase(Locale.ROOT), entry);
      }

      String self = selfName.toLowerCase(Locale.ROOT);

      for (PlayerTeam team : world.getScoreboard().getPlayerTeams()) {
         int order = orderOf(team.getName());
         if (order >= 0 && order <= 8) {
            for (String member : team.getPlayers()) {
               String key = member.toLowerCase(Locale.ROOT);
               if (!key.equals(self)) {
                  out.put(key, new StaffTeams.Member(member, order, StaffTeams.Rank.of(order), entries.get(key), StaffTeams.Source.TEAM));
                  learn(key, order);
               }
            }
         }
      }

      for (Entry<String, PlayerInfo> e : entries.entrySet()) {
         String key = e.getKey();
         if (!key.equals(self) && !out.containsKey(key)) {
            PlayerInfo entry = e.getValue();
            int listed = listOrderRank(entry);
            if (listed >= 0 && listed <= 8) {
               out.put(key, new StaffTeams.Member(entry.getProfile().name(), listed, StaffTeams.Rank.of(listed), entry, StaffTeams.Source.LIST_ORDER));
               learn(key, listed);
            } else {
               int badge = badgeOrder(entry.getTabListDisplayName());
               Integer known = knownOrder(key);
               if (badge >= 0) {
                  int order = known != null ? known : badge;
                  out.put(key, new StaffTeams.Member(entry.getProfile().name(), order, rankFor(key, order), entry, StaffTeams.Source.BADGE));
               } else if (known != null) {
                  out.put(key, new StaffTeams.Member(entry.getProfile().name(), known, rankFor(key, known), entry, StaffTeams.Source.KNOWN_NAME));
               }
            }
         }
      }

      if (learnedDirty) {
         saveLearned();
      }

      return out;
   }

   private static void learn(String key, int order) {
      Integer old = LEARNED.put(key, order);
      if (old == null || old != order) {
         learnedDirty = true;
         SpecterCore.LOG
            .info("Staff Watch: learned {} is rank {} ({})", new Object[]{key, String.format(Locale.ROOT, "%02d", order), StaffTeams.Rank.of(order).single});
      }
   }

   private static void saveLearned() {
      learnedDirty = false;

      try {
         LEARNED_FILE.getParentFile().mkdirs();
         StringBuilder sb = new StringBuilder("# Staff ranks Specter read from DonutSMP (name=rank, 00 owner ... 08 helper)\n");
         new TreeMap<>(LEARNED).forEach((k, v) -> sb.append(k).append('=').append(String.format(Locale.ROOT, "%02d", v)).append('\n'));
         Files.writeString(LEARNED_FILE.toPath(), sb.toString(), StandardCharsets.UTF_8);
      } catch (Exception var1) {
         SpecterCore.LOG.warn("Staff Watch: could not save {}: {}", LEARNED_FILE, var1.toString());
      }
   }

   public static Integer knownOrder(String key) {
      Integer learned = LEARNED.get(key);
      return learned != null ? learned : KNOWN.get(key);
   }

   public static Set<String> knownNames() {
      Set<String> all = new TreeSet<>(KNOWN.keySet());
      all.addAll(LEARNED.keySet());
      return all;
   }

   public static StaffTeams.Rank knownRank(String key) {
      Integer order = knownOrder(key);
      return rankFor(key, order != null ? order : 8);
   }

   private static StaffTeams.Rank rankFor(String key, int order) {
      if (!LEARNED.containsKey(key) && (!KNOWN.containsKey(key) || UNRANKED.contains(key))) {
         return order <= 2 ? StaffTeams.Rank.DEVELOPER : StaffTeams.Rank.STAFF;
      } else {
         return StaffTeams.Rank.of(order);
      }
   }

   public static int badgeOrder(Component display) {
      if (display == null) {
         return -1;
      } else {
         int[] order = new int[]{-1};
         display.visit((style, text) -> {
            if (text.indexOf(9733) < 0) {
               return Optional.empty();
            } else {
               TextColor color = style.getColor();
               int rgb = color == null ? 'ﲈ' : color.getValue();
               int r = rgb >> 16 & 0xFF;
               int g = rgb >> 8 & 0xFF;
               int b = rgb & 0xFF;
               order[0] = r > g && b > g ? 2 : 8;
               return Optional.of(Boolean.TRUE);
            }
         }, Style.EMPTY);
         return order[0];
      }
   }

   public static String describe(ClientLevel world, ClientPacketListener handler) {
      Set<String> samples = new TreeSet<>();
      int teams = 0;

      for (PlayerTeam team : world.getScoreboard().getPlayerTeams()) {
         teams++;
         if (samples.size() < 12) {
            samples.add(team.getName());
         }
      }

      int stars = 0;
      int ranked = 0;

      for (PlayerInfo entry : handler.getOnlinePlayers()) {
         if (badgeOrder(entry.getTabListDisplayName()) >= 0) {
            stars++;
         }

         int r = listOrderRank(entry);
         if (r >= 0 && r <= 8) {
            ranked++;
         }
      }

      return teams
         + " teams (e.g. "
         + samples
         + "), "
         + handler.getOnlinePlayers().size()
         + " tab entries, "
         + stars
         + " with a star badge, "
         + ranked
         + " with a staff sort number";
   }

   public static Map<String, StaffTeams.Member> scan(ClientLevel world, ClientPacketListener handler, String selfName) {
      return scan(world, handler, selfName, new LinkedHashMap<>());
   }

   public static int orderOf(String team) {
      if (team.length() >= 5 && team.charAt(2) == '-') {
         char a = team.charAt(0);
         char b = team.charAt(1);
         return a >= 48 && a <= 57 && b >= 48 && b <= 57 ? (a - 48) * 10 + (b - 48) : -1;
      } else {
         return -1;
      }
   }

   static {
      try {
         if (LEARNED_FILE.isFile()) {
            for (String line : Files.readAllLines(LEARNED_FILE.toPath(), StandardCharsets.UTF_8)) {
               int eq = line.indexOf(61);
               if (eq > 0 && !line.startsWith("#")) {
                  try {
                     int order = Integer.parseInt(line.substring(eq + 1).trim());
                     if (order >= 0 && order <= 8) {
                        LEARNED.put(line.substring(0, eq).trim().toLowerCase(Locale.ROOT), order);
                     }
                  } catch (NumberFormatException var4) {
                  }
               }
            }
         }
      } catch (Exception var5) {
         SpecterCore.LOG.warn("Staff Watch: could not read {}: {}", LEARNED_FILE, var5.toString());
      }

      UNRANKED = Set.of("cryptodaveyt", "showered", "bobisfound", "noahvdaa", "lzouzmp5", "splaterd", "_chaon", "dough4", "evify");
   }

   public record Member(String name, int order, StaffTeams.Rank rank, PlayerInfo entry, StaffTeams.Source source) {
      public boolean developer() {
         return this.order <= 2;
      }
   }

   public static enum Rank {
      OWNER("Owner", "owner"),
      STAFF("Staff", "staff"),
      DEVELOPER("Developers", "developer"),
      SENIOR_ADMIN("Senior Admins", "senior admin"),
      ADMIN("Admins", "admin"),
      SENIOR_MOD("Senior Mods", "senior mod"),
      MOD("Mods", "mod"),
      SENIOR_HELPER("Senior Helpers", "senior helper"),
      HELPER("Helpers", "helper");

      public final String plural;
      public final String single;

      private Rank(String plural, String single) {
         this.plural = plural;
         this.single = single;
      }

      public static StaffTeams.Rank of(int order) {
         return switch (order) {
            case 0 -> OWNER;
            default -> STAFF;
            case 2 -> DEVELOPER;
            case 3 -> SENIOR_ADMIN;
            case 4 -> ADMIN;
            case 5 -> SENIOR_MOD;
            case 6 -> MOD;
            case 7 -> SENIOR_HELPER;
            case 8 -> HELPER;
         };
      }
   }

   public static enum Source {
      TEAM,
      BADGE,
      KNOWN_NAME,
      LIST_ORDER;
   }
}
