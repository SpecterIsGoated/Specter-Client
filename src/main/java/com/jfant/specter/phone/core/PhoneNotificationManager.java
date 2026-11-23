package com.jfant.specter.phone.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfant.specter.notify.Notifications;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * One place every app posts notifications to. Keeps the history (saved, newest first, capped), unread counts for
 * icon badges, the banner queue for the phone screen, and respects each app's on/off and sound choice. While the
 * phone is put away, notifications also show as Specter toasts (if that is switched on).
 */
public final class PhoneNotificationManager {
   private static final Path FILE = PhoneConfig.DIR.resolve("notifications.json");
   private static final int MAX = 120;

   public static final class Notification {
      public final long id;
      public final String app;
      public final String title;
      public final String text;
      @Nullable
      public final String route;
      public final long time;
      public boolean read;
      /** Buttons (not saved: they only make sense for a minute or two). */
      public java.util.List<com.jfant.specter.phone.api.NotificationAction> actions = java.util.List.of();
      /** Until when the buttons work (epoch millis). */
      public long actionsUntil;

      public boolean actionsLive() {
         return !this.actions.isEmpty() && System.currentTimeMillis() < this.actionsUntil;
      }

      Notification(long id, String app, String title, String text, @Nullable String route, long time, boolean read) {
         this.id = id;
         this.app = app;
         this.title = title;
         this.text = text;
         this.route = route;
         this.time = time;
         this.read = read;
      }
   }

   private final PhoneConfig config;
   private final List<Notification> history = new ArrayList<>();
   private final ArrayDeque<Notification> banners = new ArrayDeque<>();
   private long nextId = 1;
   private boolean dirty;
   private long dirtyAt;
   private Runnable sound = () -> {
   };
   private Function<String, String> appName = id -> id;
   private java.util.function.BooleanSupplier phoneOut = () -> false;

   public PhoneNotificationManager(PhoneConfig config) {
      this.config = config;
      this.load();
   }

   public void post(String app, String title, String text, @Nullable String route) {
      this.post(app, title, text, route, java.util.List.of(), 0);
   }

   /** A notification with buttons that work for {@code actionsMillis} (see NotificationAction). */
   public void post(String app, String title, String text, @Nullable String route, java.util.List<com.jfant.specter.phone.api.NotificationAction> actions,
                    long actionsMillis) {
      PhoneConfig.NotifyPrefs prefs = this.config.prefs(app);
      if (!prefs.enabled) return;
      Notification n = new Notification(this.nextId++, app, clip(title, 60), clip(text, 240), route, System.currentTimeMillis(), false);
      if (actions != null && !actions.isEmpty() && actionsMillis > 0) {
         n.actions = java.util.List.copyOf(actions);
         n.actionsUntil = n.time + actionsMillis;
      }
      this.history.add(0, n);
      while (this.history.size() > MAX) this.history.remove(this.history.size() - 1);
      // banners only while you are looking at the phone; otherwise the lock screen and the toast cover it
      if (this.config.banners && this.phoneOut.getAsBoolean()) {
         this.banners.add(n);
         while (this.banners.size() > 4) this.banners.poll();
      }
      if (prefs.sound) this.sound.run();
      if (!this.phoneOut.getAsBoolean() && this.config.toastsWhenClosed) {
         Notifications.info(this.appName.apply(app) + " - " + n.title, n.text);
      }
      this.markDirty();
   }

   public List<Notification> all() {
      return Collections.unmodifiableList(this.history);
   }

   public int unread(String app) {
      int count = 0;
      for (Notification n : this.history) if (!n.read && n.app.equals(app)) count++;
      return count;
   }

   /** Unread ones, newest first (the lock screen). */
   public List<Notification> unreadList() {
      List<Notification> out = new ArrayList<>();
      for (Notification n : this.history) if (!n.read) out.add(n);
      return out;
   }

   public void markAllRead() {
      for (Notification n : this.history) n.read = true;
      this.markDirty();
   }

   public int unreadTotal() {
      int count = 0;
      for (Notification n : this.history) if (!n.read) count++;
      return count;
   }

   public void markRead(String app) {
      boolean changed = false;
      for (Notification n : this.history) {
         if (!n.read && n.app.equals(app)) {
            n.read = true;
            changed = true;
         }
      }
      if (changed) this.markDirty();
   }

   public void dismiss(Notification n) {
      if (this.history.remove(n)) this.markDirty();
      this.banners.remove(n);
   }

   public void clear() {
      this.history.clear();
      this.banners.clear();
      this.markDirty();
   }

   /** The banner showing now (null when none), and drops it once its time is up. */
   @Nullable
   public Notification banner(long shownFor) {
      Notification n = this.banners.peek();
      if (n != null && shownFor > bannerMillis(n)) {
         this.banners.poll();
         return null;
      }
      return n;
   }

   /** How long a banner stays: longer when it has buttons (a teleport request needs time to answer). */
   public static long bannerMillis(Notification n) {
      return n.actionsLive() ? 8000 : 3800;
   }

   public void clearBanners() {
      this.banners.clear();
   }

   public void skipBanner() {
      this.banners.poll();
   }

   /** The phone wires in its sound, app names and whether it is out (for toasts). */
   public void setHooks(Runnable sound, Function<String, String> appName, java.util.function.BooleanSupplier phoneOut) {
      this.sound = sound;
      this.appName = appName;
      this.phoneOut = phoneOut;
   }

   public void tick() {
      if (this.dirty && System.currentTimeMillis() - this.dirtyAt > 2000) this.saveNow();
   }

   public void saveNow() {
      this.dirty = false;
      JsonArray arr = new JsonArray();
      for (Notification n : this.history) {
         JsonObject o = new JsonObject();
         o.addProperty("app", n.app);
         o.addProperty("title", n.title);
         o.addProperty("text", n.text);
         if (n.route != null) o.addProperty("route", n.route);
         o.addProperty("time", n.time);
         o.addProperty("read", n.read);
         arr.add(o);
      }
      JsonObject root = new JsonObject();
      root.add("history", arr);
      PhoneConfig.writeAtomic(FILE, PhoneConfig.pretty(root));
   }

   private void load() {
      JsonObject root = PhoneConfig.readJson(FILE);
      if (!root.has("history") || !root.get("history").isJsonArray()) return;
      for (JsonElement e : root.getAsJsonArray("history")) {
         try {
            JsonObject o = e.getAsJsonObject();
            this.history.add(new Notification(this.nextId++, o.get("app").getAsString(), o.get("title").getAsString(), o.get("text").getAsString(),
               o.has("route") ? o.get("route").getAsString() : null, o.get("time").getAsLong(), o.get("read").getAsBoolean()));
         } catch (RuntimeException ignored) {
         }
      }
   }

   private void markDirty() {
      this.dirty = true;
      this.dirtyAt = System.currentTimeMillis();
   }

   private static String clip(String s, int max) {
      if (s == null) return "";
      return s.length() > max ? s.substring(0, max - 1) + "..." : s;
   }
}
