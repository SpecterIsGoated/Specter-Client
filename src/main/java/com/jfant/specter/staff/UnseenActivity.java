package com.jfant.specter.staff;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class UnseenActivity {
   private static final Map<String, String> SOUND_THINGS = new LinkedHashMap<>();
   private static final String[] SOUND_ACTIONS = new String[]{".open", ".close", ".locked", ".click"};
   private static final String[] AMBIENT_SMOKE_SOURCES = new String[]{"campfire", "furnace", "smoker", "torch", "fire", "candle", "respawn_anchor"};
   private static final int QUEUE_LIMIT = 2048;
   private static final int PER_POLL = 256;
   private static final int BLASTS_KEPT = 8;
   private static final long YOUR_SOUND_MS = 1000L;
   private static final long YOUR_SPARKS_MS = 900L;
   private static final long YOUR_BLOCK_MS = 6000L;
   private static final long YOUR_CONTAINER_MS = 2500L;
   private static final long BLAST_MS = 3000L;
   private static final long PAIR_MS = 2000L;
   private static final long REPEAT_MS = 3000L;
   private static final long POWER_MEMORY_MS = 5000L;
   private static final double CAUSE_RANGE = 4.0;
   private static final double CLOSE_TO_YOU = 2.5;
   private static final double VILLAGER_RANGE = 3.0;
   private final ConcurrentLinkedQueue<UnseenActivity.Event> inbox = new ConcurrentLinkedQueue<>();
   private final Map<Long, Long> yourBlocks = new ConcurrentHashMap<>();
   private volatile long lastYouActed;
   private final List<UnseenActivity.Blast> blasts = new ArrayList<>();
   private final Map<Long, Long> poweredUntil = new HashMap<>();
   private final Map<String, Long> firstOfPair = new HashMap<>();
   private final Map<UnseenActivity.Channel, Long> lastReported = new EnumMap<>(UnseenActivity.Channel.class);
   private long containerOpenAt;
   private final List<UnseenActivity.Excuse> soundExcuses = List.of(
      (e, c) -> c.now() - this.lastYouActed < 1000L,
      (e, c) -> this.yourBlocks.getOrDefault(BlockPos.containing(e.at()).asLong(), 0L) > c.now(),
      (e, c) -> isContainer(e.id()) && c.now() - this.containerOpenAt < 2500L,
      (e, c) -> isDoorLike(e.id()) && this.powered(c.world(), e.at(), c.now()),
      (e, c) -> isDoorLike(e.id()) && !e.id().contains("trapdoor") && villagerNear(c.world(), e.at())
   );
   private final List<UnseenActivity.Excuse> sparkExcuses = List.of(
      (e, c) -> c.now() - this.lastYouActed < 900L,
      (e, c) -> isWeak(e.id()) && e.at().closerThan(c.self().position(), 2.5),
      (e, c) -> e.id().contains("smoke") && smokeSourceNear(c.world(), e.at()),
      (e, c) -> isWeak(e.id()) && !this.secondOfPair(e.id(), c.now())
   );

   public void reset() {
      this.inbox.clear();
      this.yourBlocks.clear();
      this.lastYouActed = 0L;
      this.blasts.clear();
      this.poweredUntil.clear();
      this.firstOfPair.clear();
      this.lastReported.clear();
      this.containerOpenAt = 0L;
   }

   public void sound(ClientboundSoundPacket packet) {
      String id = packet.getSound().unwrapKey().map(key -> lower(key.identifier())).orElse("");
      if (suspiciousSound(id)) {
         this.queue(new UnseenActivity.Event(UnseenActivity.Channel.NOISE, id, new Vec3(packet.getX(), packet.getY(), packet.getZ()), 0.0));
      }
   }

   public void particle(ClientboundLevelParticlesPacket packet) {
      Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.particle().getType());
      String id = key == null ? "" : lower(key);
      if (suspiciousParticle(id)) {
         this.queue(new UnseenActivity.Event(UnseenActivity.Channel.SPARKS, id, new Vec3(packet.x(), packet.y(), packet.z()), 0.0));
      }
   }

   public void explosion(ClientboundExplodePacket packet) {
      this.queue(new UnseenActivity.Event(null, "", packet.center(), (double)packet.radius()));
   }

   public void sent(Packet<?> packet) {
      long now = System.currentTimeMillis();
      if (packet instanceof ServerboundUseItemOnPacket use) {
         BlockPos clicked = use.hitResult().getBlockPos();
         this.yourBlocks.put(clicked.asLong(), now + 6000L);
         this.yourBlocks.put(clicked.relative(use.hitResult().getDirection()).asLong(), now + 6000L);
         this.lastYouActed = now;
      } else if (packet instanceof ServerboundPlayerActionPacket
         || packet instanceof ServerboundUseItemPacket
         || packet instanceof ServerboundInteractPacket
         || packet instanceof ServerboundPunchPacket || packet instanceof ServerboundAttackPacket) {
         this.lastYouActed = now;
      }
   }

   private void queue(UnseenActivity.Event event) {
      if (this.inbox.size() < 2048) {
         this.inbox.add(event);
      }
   }

   public List<UnseenActivity.Signal> poll(long now, double range) {
      Minecraft mc = Minecraft.getInstance();
      List<UnseenActivity.Signal> reports = new ArrayList<>(0);
      if (mc.player != null && mc.level != null) {
         UnseenActivity.Context context = new UnseenActivity.Context(mc.level, mc.player, now);
         this.forget(now);
         if (mc.player.containerMenu != mc.player.inventoryMenu) {
            this.containerOpenAt = now;
         }

         Vec3 you = mc.player.position();

         for (int handled = 0; handled < 256; handled++) {
            UnseenActivity.Event event = this.inbox.poll();
            if (event == null) {
               break;
            }

            if (event.channel() == null) {
               this.rememberBlast(event, now);
            } else if (event.at().closerThan(you, range) && !inPlainView(context, event.at()) && !this.setOffByBlast(event.at())) {
               List<UnseenActivity.Excuse> excuses = event.channel() == UnseenActivity.Channel.NOISE ? this.soundExcuses : this.sparkExcuses;
               if (!excuses.stream().anyMatch(excuse -> excuse.explains(event, context))
                  && now - this.lastReported.getOrDefault(event.channel(), -4611686018427387904L) >= 3000L) {
                  this.lastReported.put(event.channel(), now);
                  String what = event.channel() == UnseenActivity.Channel.NOISE ? soundText(event.id()) : particleText(event.id());
                  reports.add(new UnseenActivity.Signal(what, whereFrom(you, event.at())));
               }
            }
         }

         return reports;
      } else {
         this.inbox.clear();
         return reports;
      }
   }

   private void forget(long now) {
      this.yourBlocks.values().removeIf(until -> until <= now);
      this.poweredUntil.values().removeIf(until -> until <= now);
      this.firstOfPair.values().removeIf(at -> now - at > 2000L);
      this.blasts.removeIf(blast -> now - blast.at_ms() > 3000L);
   }

   private void rememberBlast(UnseenActivity.Event event, long now) {
      this.blasts.add(new UnseenActivity.Blast(event.at(), Math.max(2.0, event.size() + 4.0), now));

      while (this.blasts.size() > 8) {
         this.blasts.removeFirst();
      }
   }

   private boolean setOffByBlast(Vec3 at) {
      for (UnseenActivity.Blast blast : this.blasts) {
         if (blast.at().closerThan(at, blast.reach())) {
            return true;
         }
      }

      return false;
   }

   private static boolean inPlainView(UnseenActivity.Context context, Vec3 at) {
      for (Entity entity : context.world().entitiesForRendering()) {
         if (entity != context.self() && entity.position().closerThan(at, 4.0)) {
            if (entity instanceof Projectile) {
               return true;
            }

            if (entity instanceof Player player && !player.isInvisible()) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean powered(ClientLevel world, Vec3 at, long now) {
      BlockPos base = BlockPos.containing(at);

      for (BlockPos pos : new BlockPos[]{base.below(), base, base.above()}) {
         if (this.poweredUntil.getOrDefault(pos.asLong(), 0L) > now) {
            return true;
         }

         BlockState state = world.getBlockState(pos);
         boolean on = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
         if (on || world.hasNeighborSignal(pos)) {
            this.poweredUntil.put(pos.asLong(), now + 5000L);
            return true;
         }
      }

      return false;
   }

   private static boolean villagerNear(ClientLevel world, Vec3 at) {
      for (Entity entity : world.entitiesForRendering()) {
         if (entity.getType() == EntityTypes.VILLAGER && entity.position().closerThan(at, 3.0)) {
            return true;
         }
      }

      return false;
   }

   private static boolean smokeSourceNear(ClientLevel world, Vec3 at) {
      BlockPos base = BlockPos.containing(at);

      for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -2, -2), base.offset(2, 2, 2))) {
         String block = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).getPath();

         for (String source : AMBIENT_SMOKE_SOURCES) {
            if (block.contains(source)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean secondOfPair(String id, long now) {
      Long first = this.firstOfPair.get(id);
      if (first != null && now - first <= 2000L) {
         this.firstOfPair.remove(id);
         return true;
      } else {
         this.firstOfPair.put(id, now);
         return false;
      }
   }

   public static boolean suspiciousSound(String path) {
      if (soundThing(path) == null) {
         return false;
      } else {
         for (String action : SOUND_ACTIONS) {
            if (path.contains(action)) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean suspiciousParticle(String path) {
      return path.equals("block") || path.contains("smoke") || path.contains("crit") || path.contains("enchanted_hit") || path.contains("damage_indicator");
   }

   private static String soundThing(String path) {
      for (Entry<String, String> entry : SOUND_THINGS.entrySet()) {
         if (path.contains(entry.getKey())) {
            String key = entry.getKey();
            boolean clicks = key.equals("button") || key.equals("lever");
            return clicks == path.contains(".click") ? entry.getValue() : null;
         }
      }

      return null;
   }

   private static boolean isContainer(String path) {
      return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
   }

   private static boolean isDoorLike(String path) {
      return path.contains("door") || path.contains("fence_gate");
   }

   private static boolean isWeak(String path) {
      return path.equals("block") || path.contains("smoke");
   }

   private static String soundText(String path) {
      String thing = soundThing(path);
      String done = path.contains(".open") ? "opened" : (path.contains(".close") ? "closed" : (path.contains(".locked") ? "tried (locked)" : "used"));
      return "A " + (thing == null ? "something" : thing) + " was " + done;
   }

   private static String particleText(String path) {
      if (path.contains("enchanted_hit")) {
         return "Enchanted hit particles";
      } else if (path.contains("damage_indicator")) {
         return "Damage particles";
      } else if (path.contains("crit")) {
         return "Hit particles";
      } else {
         return path.contains("smoke") ? "Smoke" : "Block-break particles";
      }
   }

   private static String whereFrom(Vec3 you, Vec3 at) {
      double dx = at.x - you.x;
      double dz = at.z - you.z;
      StringBuilder where = new StringBuilder().append(Math.round(at.distanceTo(you))).append(" blocks");
      String direction = (dz < -1.0 ? "N" : (dz > 1.0 ? "S" : "")) + (dx > 1.0 ? "E" : (dx < -1.0 ? "W" : ""));
      if (!direction.isEmpty()) {
         where.append(' ').append(direction);
      }

      return where.toString();
   }

   private static String lower(Identifier id) {
      return id.getPath().toLowerCase(Locale.ROOT);
   }

   static {
      SOUND_THINGS.put("shulker_box", "shulker box");
      SOUND_THINGS.put("chest", "chest");
      SOUND_THINGS.put("barrel", "barrel");
      SOUND_THINGS.put("trapdoor", "trapdoor");
      SOUND_THINGS.put("fence_gate", "gate");
      SOUND_THINGS.put("door", "door");
      SOUND_THINGS.put("button", "button");
      SOUND_THINGS.put("lever", "lever");
   }

   private record Blast(Vec3 at, double reach, long at_ms) {
   }

   private static enum Channel {
      NOISE,
      SPARKS;
   }

   private record Context(ClientLevel world, LocalPlayer self, long now) {
   }

   private record Event(UnseenActivity.Channel channel, String id, Vec3 at, double size) {
   }

   private interface Excuse {
      boolean explains(UnseenActivity.Event var1, UnseenActivity.Context var2);
   }

   public record Signal(String what, String where) {
   }
}
