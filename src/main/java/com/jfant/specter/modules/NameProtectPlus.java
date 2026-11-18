package com.jfant.specter.modules;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.core.events.world.TickEvent;
import com.jfant.specter.core.settings.EnumSetting;
import com.jfant.specter.core.settings.Setting;
import com.jfant.specter.core.settings.SettingGroup;
import com.jfant.specter.core.settings.StringSetting;
import com.jfant.specter.core.systems.modules.Module;
import com.jfant.specter.names.AliasRules;
import com.jfant.specter.names.NameTable;
import com.jfant.specter.names.NameText;
import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action;
import net.minecraft.world.entity.player.PlayerSkin;

public class NameProtectPlus extends Module {
   private static final int DEPARTED_MEMORY = 256;
   private static final int SEEN_MEMORY = 8192;
   private static final int MIN_OTHER_NAME_LENGTH = 3;
   private static final int NPC_UUID_VERSION = 2;
   private static final int CHAT_REFRESH_COOLDOWN_TICKS = 20;
   private static final int REFUSAL_NOTICE_DELAY_TICKS = 30;
   private static volatile NameTable active;
   private static volatile NameProtectPlus running;
   private static volatile int generation;
   private static volatile boolean failureLogged;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<String> selfAlias = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("your-alias")
            .description(
               "What is shown instead of your name. Up to 16 letters, digits and underscores. A name that reads like staff, or is a player's, is not used."
            )
            .defaultValue("You")
            .onChanged(value -> this.markEdited())
            .build()
      );
   private final Setting<String> extraNames = this.sgGeneral
      .add(
         new StringSetting.Builder()
            .name("extra-names")
            .description("Other names of yours to hide too - an alt, an old name. Separate with spaces or commas.")
            .defaultValue("")
            .onChanged(value -> this.markEdited())
            .build()
      );
   private final Setting<NameProtectPlus.Others> others = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("others"))
                     .description(
                        "What to show for other players. Keep: their names. Same label: all of them as the label below. Numbered: the label with a number each, so you can tell them apart."
                     ))
                  .defaultValue(NameProtectPlus.Others.Keep))
               .onChanged(value -> this.markEdited()))
            .build()
      );
   private final Setting<NameProtectPlus.Label> othersLabel = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder()
                           .name("others-label"))
                        .description("The word shown for other players."))
                     .defaultValue(NameProtectPlus.Label.Player))
                  .visible(() -> this.others.get() != NameProtectPlus.Others.Keep))
               .onChanged(value -> this.markEdited()))
            .build()
      );
   private final LinkedHashMap<String, String> knownOthers = new LinkedHashMap<>();
   private final Map<String, Integer> numbers = new HashMap<>();
   private int nextNumber = 1;
   private final LinkedHashSet<String> seenSkeletons = new LinkedHashSet<>();
   private WeakReference<ClientPacketListener> trackedConnection = new WeakReference<>(null);
   private volatile boolean dirty = true;
   private volatile boolean settingsEdited;
   private int builtListSize = -1;
   private String builtProfileName;
   private NameTable table = NameTable.EMPTY;
   private String refusedAlias;
   private AliasRules.Refusal refusalReason;
   private String noticedAlias;
   private int refusalNoticeDelay;
   private boolean chatRefreshPending;
   private int chatRefreshCooldown;

   public NameProtectPlus() {
      super(
         SpecterClientAddon.TOOLS,
         "name-protect-plus",
         "Shows aliases instead of player names on your screen: yours, any extra names of yours, and a label for everyone else."
      );
   }

   public static Component protect(Component text) {
      NameTable current = active;
      if (current != null && text != null) {
         try {
            return NameText.rewrite(current, text);
         } catch (RuntimeException var3) {
            reportFailure(var3);
            return text;
         }
      } else {
         return text;
      }
   }

   public static FormattedText protectText(FormattedText text) {
      NameTable current = active;
      if (current != null && text != null) {
         try {
            return NameText.rewrite(current, text);
         } catch (RuntimeException var3) {
            reportFailure(var3);
            return text;
         }
      } else {
         return text;
      }
   }

   public static boolean hidesName(String name) {
      NameTable current = active;
      return current != null && name != null && current.aliasAt(name, 0, name.length()) != null;
   }

   public static int generation() {
      return generation;
   }

   public static PlayerSkin hiddenHead(UUID profileId) {
      return DefaultPlayerSkin.get(profileId);
   }

   public static void onPlayerInfoApplied(ClientboundPlayerInfoUpdatePacket packet) {
      NameProtectPlus module = running;
      if (module != null && packet != null) {
         EnumSet<Action> actions = packet.actions();
         if (actions.contains(Action.ADD_PLAYER) || actions.contains(Action.UPDATE_LISTED)) {
            module.dirty = true;
         }
      }
   }

   private static void reportFailure(RuntimeException failure) {
      if (!failureLogged) {
         failureLogged = true;
         SpecterClientAddon.LOG.error("Name Protect Plus could not rewrite a text; showing it unchanged", failure);
      }
   }

   @Override
   public void onActivate() {
      running = this;
      this.syncConnection();
      this.settingsEdited = false;
      this.rebuild();
      this.chatRefreshPending = true;
      this.chatRefreshCooldown = 0;
   }

   @Override
   public void onDeactivate() {
      running = null;
      active = null;
      generation++;
      this.table = NameTable.EMPTY;
      this.chatRefreshPending = false;
      this.dirty = true;
      this.refusedAlias = null;
      this.noticedAlias = null;
      this.mc.execute(() -> {
         if (this.mc.gui != null) {
            this.mc.gui.hud.getChat().rescaleChat();
         }
      });
   }

   private void markEdited() {
      this.settingsEdited = true;
      this.dirty = true;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      this.syncConnection();
      if (listSize(this.mc.getConnection()) != this.builtListSize) {
         this.dirty = true;
      }

      if (!Objects.equals(this.profileName(), this.builtProfileName)) {
         this.dirty = true;
      }

      if (this.dirty) {
         this.rebuild();
      }

      this.announceRefusal();
      if (this.chatRefreshCooldown > 0) {
         this.chatRefreshCooldown--;
      }

      if (this.chatRefreshPending && this.chatRefreshCooldown == 0 && !this.chatFocused() && this.mc.gui != null) {
         this.chatRefreshPending = false;
         this.chatRefreshCooldown = 20;
         this.mc.gui.hud.getChat().rescaleChat();
      }
   }

   private void syncConnection() {
      ClientPacketListener connection = this.mc.getConnection();
      if (connection != this.trackedConnection.get()) {
         this.trackedConnection = new WeakReference<>(connection);
         this.knownOthers.clear();
         this.numbers.clear();
         this.nextNumber = 1;
         this.builtListSize = -1;
         this.dirty = true;
      }
   }

   private void rebuild() {
      this.dirty = false;
      boolean edited = this.settingsEdited;
      this.settingsEdited = false;
      ClientPacketListener connection = this.mc.getConnection();
      String accountName = NameTable.core(this.mc.getUser() != null ? this.mc.getUser().getName() : null);
      this.builtProfileName = this.profileName();
      String profile = NameTable.core(this.builtProfileName);
      this.builtListSize = listSize(connection);
      List<String> extras = new ArrayList<>();

      for (String raw : this.extraNames.get().split("[,;\\s]+")) {
         String extra = NameTable.core(raw);
         if (extra != null) {
            extras.add(extra);
         }
      }

      List<String> ownNames = new ArrayList<>(extras.size() + 2);
      if (accountName != null) {
         ownNames.add(accountName);
      }

      if (profile != null) {
         ownNames.add(profile);
      }

      ownNames.addAll(extras);
      this.rememberOnline(connection, accountName, profile);
      String ownAlias = this.resolveOwnAlias(extras);
      Map<String, String> names = new LinkedHashMap<>();

      for (String own : ownNames) {
         put(names, own, ownAlias);
      }

      if (this.others.get() != NameProtectPlus.Others.Keep) {
         this.trackOthers(connection, ownNames);
         String label = this.othersLabel.get().name();
         boolean numbered = this.others.get() == NameProtectPlus.Others.Numbered;

         for (Entry<String, String> known : this.knownOthers.entrySet()) {
            put(names, known.getValue(), numbered ? label + " " + this.numbers.get(known.getKey()) : label);
         }
      }

      this.publish(new NameTable(names), edited);
   }

   private void publish(NameTable next, boolean edited) {
      NameTable previous = this.table;
      this.table = next;
      active = next;
      if (!next.sameAs(previous)) {
         generation++;
         if (edited || !next.newSince(previous).isEmpty()) {
            this.chatRefreshPending = true;
         }
      }
   }

   private String resolveOwnAlias(List<String> extras) {
      String alias = AliasRules.sanitize(this.selfAlias.get());
      if (!alias.isEmpty() && !alias.equalsIgnoreCase("You")) {
         Set<String> extraSkeletons = new HashSet<>();

         for (String extra : extras) {
            extraSkeletons.add(AliasRules.skeleton(extra));
         }

         AliasRules.Refusal refusal = AliasRules.check(alias, skeleton -> this.seenSkeletons.contains(skeleton) || extraSkeletons.contains(skeleton));
         if (refusal == null) {
            this.refusedAlias = null;
            return alias;
         } else {
            if (!alias.equals(this.refusedAlias)) {
               this.refusedAlias = alias;
               this.refusalNoticeDelay = 30;
            }

            this.refusalReason = refusal;
            return "You";
         }
      } else {
         this.refusedAlias = null;
         return alias.isEmpty() ? "You" : alias;
      }
   }

   private void announceRefusal() {
      if (this.refusedAlias == null) {
         this.noticedAlias = null;
      } else if (!this.refusedAlias.equals(this.noticedAlias)) {
         if (this.refusalNoticeDelay > 0) {
            this.refusalNoticeDelay--;
         } else if (this.mc.player != null) {
            this.noticedAlias = this.refusedAlias;
            this.warning("The alias \"%s\" is not used: %s. Showing \"%s\" instead.", new Object[]{this.refusedAlias, this.refusalReason.reason(), "You"});
         }
      }
   }

   private void rememberOnline(ClientPacketListener connection, String accountName, String profile) {
      if (connection != null) {
         for (PlayerInfo entry : connection.getListedOnlinePlayers()) {
            GameProfile gameProfile = entry.getProfile();
            String name = NameTable.core(gameProfile != null ? gameProfile.name() : null);
            if (name != null && !name.equalsIgnoreCase(accountName) && !name.equalsIgnoreCase(profile)) {
               String skeleton = AliasRules.skeleton(name);
               this.seenSkeletons.remove(skeleton);
               this.seenSkeletons.add(skeleton);
            }
         }

         Iterator<String> oldest = this.seenSkeletons.iterator();

         while (this.seenSkeletons.size() > 8192 && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
         }
      }
   }

   private void trackOthers(ClientPacketListener connection, List<String> own) {
      if (connection != null) {
         int online = 0;

         for (PlayerInfo entry : connection.getOnlinePlayers()) {
            GameProfile profile = entry.getProfile();
            if (profile != null && (profile.id() == null || profile.id().version() != 2)) {
               String name = NameTable.core(profile.name());
               if (isLikelyPlayer(name) && !isOwn(name, own)) {
                  String key = name.toLowerCase(Locale.ROOT);
                  this.knownOthers.remove(key);
                  this.knownOthers.put(key, name);
                  this.numbers.computeIfAbsent(key, k -> this.nextNumber++);
                  online++;
               }
            }
         }

         Iterator<String> oldest = this.knownOthers.keySet().iterator();

         while (this.knownOthers.size() > online + 256 && oldest.hasNext()) {
            this.numbers.remove(oldest.next());
            oldest.remove();
         }
      }
   }

   private static void put(Map<String, String> names, String rawName, String alias) {
      String name = NameTable.core(rawName);
      if (name != null && !alias.equalsIgnoreCase(name)) {
         names.putIfAbsent(name, alias);
      }
   }

   private static boolean isLikelyPlayer(String name) {
      if (name != null && name.length() >= 3) {
         for (int i = 0; i < name.length(); i++) {
            if (Character.isLetter(name.charAt(i))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean isOwn(String name, List<String> own) {
      for (String ownName : own) {
         if (ownName.equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   private String profileName() {
      return this.mc.player != null ? this.mc.player.getGameProfile().name() : null;
   }

   private static int listSize(ClientPacketListener connection) {
      return connection != null ? connection.getOnlinePlayers().size() : -1;
   }

   private boolean chatFocused() {
      return this.mc.gui != null && this.mc.gui.hud.getChat().isChatFocused();
   }

   public static enum Label {
      Player,
      Anonymous,
      Unknown;
   }

   public static enum Others {
      Keep("Keep"),
      SameLabel("Same label"),
      Numbered("Numbered");

      private final String label;

      private Others(String label) {
         this.label = label;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }
}
