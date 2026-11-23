package com.jfant.specter.phone.core;

import com.google.gson.JsonObject;
import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.phone.PhoneOS;
import com.jfant.specter.phone.api.AppInfo;
import com.jfant.specter.phone.api.AppPermission;
import com.jfant.specter.phone.api.Hooks;
import com.jfant.specter.phone.api.PhoneApplication;
import com.jfant.specter.phone.api.PhoneContext;
import com.jfant.specter.phone.api.PhoneSound;
import com.jfant.specter.phone.ui.PhoneUi;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the apps: creates them on first use, moves them to the front and back, ticks them, and keeps each one in a
 * sandbox of try/catch - an exception from any app method marks that app crashed (it shows an error card with
 * Reopen) and never reaches the phone or the game.
 */
public final class PhoneApplicationManager {
   private final PhoneApplicationRegistry registry;
   private final Map<String, Running> running = new LinkedHashMap<>();

   public final class Running {
      public final PhoneApplicationRegistry.Entry entry;
      public final PhoneUi ui = new PhoneUi();
      final AppContext context;
      @Nullable
      PhoneApplication app;
      @Nullable
      public Throwable crash;
      boolean front;
      long lastUsed;

      Running(PhoneApplicationRegistry.Entry entry) {
         this.entry = entry;
         this.context = new AppContext(entry.info());
      }

      public AppInfo info() {
         return this.entry.info();
      }

      @Nullable
      public PhoneApplication app() {
         return this.crash == null ? this.app : null;
      }

      public PhoneContext context() {
         return this.context;
      }

      public long lastUsedMillis() {
         return this.lastUsed;
      }
   }

   public PhoneApplicationManager(PhoneApplicationRegistry registry) {
      this.registry = registry;
   }

   @Nullable
   public Running running(String id) {
      return this.running.get(id);
   }

   /** The app's runtime, creating the app if this is its first use. Null for an unknown id. */
   @Nullable
   public Running launch(String id) {
      Running r = this.running.get(id);
      if (r != null) return r;
      PhoneApplicationRegistry.Entry entry = this.registry.get(id);
      if (entry == null) return null;
      Running fresh = new Running(entry);
      this.running.put(id, fresh);
      this.guard(fresh, "create", () -> {
         PhoneApplication app = entry.factory().get();
         app.attach(fresh.context);
         fresh.app = app;
         Hooks.create(app);
      });
      return fresh;
   }

   public void toFront(Running r, @Nullable String route) {
      r.lastUsed = System.currentTimeMillis();
      if (!r.front) {
         r.front = true;
         this.guard(r, "open", () -> Hooks.open(r.app));
      }
      if (route != null && !route.isEmpty()) this.guard(r, "route", () -> Hooks.route(r.app, route));
   }

   public void toBack(Running r) {
      if (!r.front) return;
      r.front = false;
      r.ui.reset();
      this.guard(r, "close", () -> Hooks.close(r.app));
      r.context.flush();
   }

   /** Ends an app (closed from the switcher, or reopened after a crash). */
   public void kill(String id) {
      Running r = this.running.remove(id);
      if (r == null) return;
      if (r.front) this.toBack(r);
      if (r.app != null && r.crash == null) this.guard(r, "destroy", () -> Hooks.destroy(r.app));
      r.context.flush();
   }

   public void killAll() {
      for (String id : new ArrayList<>(this.running.keySet())) this.kill(id);
   }

   /** Running apps, most recently used first (the switcher). Background-only services are left out. */
   public List<Running> recents() {
      List<Running> list = new ArrayList<>();
      for (Running r : this.running.values()) if (r.lastUsed > 0) list.add(r);
      list.sort(Comparator.comparingLong((Running r) -> r.lastUsed).reversed());
      return list;
   }

   public boolean guard(Running r, String what, Runnable action) {
      if (r.crash != null) return false;
      try {
         action.run();
         return true;
      } catch (Throwable t) {
         this.crashed(r, what, t);
         return false;
      }
   }

   public <T> T guard(Running r, String what, Supplier<T> action, T fallback) {
      if (r.crash != null) return fallback;
      try {
         return action.get();
      } catch (Throwable t) {
         this.crashed(r, what, t);
         return fallback;
      }
   }

   private void crashed(Running r, String what, Throwable t) {
      if (t instanceof VirtualMachineError vm && !(t instanceof StackOverflowError)) throw vm;
      r.crash = t;
      r.ui.reset();
      SpecterClientAddon.LOG.error("Donut Phone: app '{}' crashed in {}", r.info().id(), what, t);
      PhoneApplication app = r.app;
      if (app != null) {
         try {
            Hooks.destroy(app);
         } catch (Throwable ignored) {
         }
      }
   }

   public void tick(@Nullable Running front) {
      if (front != null && front.app() != null) this.guard(front, "tick", front.app()::tick);
      for (PhoneApplicationRegistry.Entry e : this.registry.all()) {
         if (!e.info().background()) continue;
         Running r = this.launch(e.info().id());
         if (r != null && r.app() != null) this.guard(r, "background tick", r.app()::backgroundTick);
      }
      for (Running r : this.running.values()) r.context.tickSave();
   }

   // ============================================================================================== context

   private final class AppContext implements PhoneContext {
      private final AppInfo info;
      private JsonObject storage;
      private boolean dirty;
      private long dirtyAt;
      private final Set<AppPermission> warned = EnumSet.noneOf(AppPermission.class);

      AppContext(AppInfo info) {
         this.info = info;
      }

      private boolean allowed(AppPermission p) {
         if (this.info.may(p)) return true;
         if (this.warned.add(p)) SpecterClientAddon.LOG.warn("Donut Phone: app '{}' used {} without declaring it; ignored", this.info.id(), p);
         return false;
      }

      private Path file() {
         return PhoneConfig.DIR.resolve("apps").resolve(this.info.id() + ".json");
      }

      @Override
      public AppInfo info() {
         return this.info;
      }

      @Override
      public Minecraft mc() {
         return Minecraft.getInstance();
      }

      @Override
      public PhoneTheme theme() {
         return PhoneOS.get().themes().theme();
      }

      @Override
      public double time() {
         return System.nanoTime() / 1.0E9;
      }

      @Override
      public void notify(String title, String text) {
         this.notify(title, text, null);
      }

      @Override
      public void notify(String title, String text, String route) {
         if (this.allowed(AppPermission.NOTIFICATIONS)) PhoneOS.get().notifications().post(this.info.id(), title, text, route);
      }

      @Override
      public void notify(String title, String text, String route, java.util.List<com.jfant.specter.phone.api.NotificationAction> actions, long actionsMillis) {
         if (!this.allowed(AppPermission.NOTIFICATIONS)) return;
         java.util.List<com.jfant.specter.phone.api.NotificationAction> ok = actions == null || actions.isEmpty() || !this.allowed(AppPermission.COMMANDS)
            ? java.util.List.of() : actions;
         PhoneOS.get().notifications().post(this.info.id(), title, text, route, ok, actionsMillis);
      }

      @Override
      public void toast(String text) {
         PhoneOS.get().toast(text);
      }

      @Override
      public void openApp(String appId) {
         PhoneOS.get().openApp(appId, null);
      }

      @Override
      public void openApp(String appId, String route) {
         PhoneOS.get().openApp(appId, route);
      }

      @Override
      public void goHome() {
         PhoneOS.get().goHome();
      }

      @Override
      public void closePhone() {
         PhoneOS.get().close();
      }

      @Override
      public void openChat(String text) {
         if (this.allowed(AppPermission.CHAT)) PhoneOS.get().openChat(text);
      }

      @Override
      public boolean runCommand(String command) {
         if (!this.allowed(AppPermission.COMMANDS)) return false;
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() == null || command == null || command.isBlank()) return false;
         mc.getConnection().sendCommand(command.startsWith("/") ? command.substring(1) : command);
         return true;
      }

      @Override
      public JsonObject storage() {
         if (this.storage == null) this.storage = PhoneConfig.readJson(this.file());
         return this.storage;
      }

      @Override
      public void saveStorage() {
         this.dirty = true;
         this.dirtyAt = System.currentTimeMillis();
      }

      void tickSave() {
         if (this.dirty && System.currentTimeMillis() - this.dirtyAt > 1500) this.flush();
      }

      void flush() {
         if (!this.dirty || this.storage == null) return;
         this.dirty = false;
         PhoneConfig.writeAtomic(this.file(), PhoneConfig.pretty(this.storage));
      }

      @Override
      public Path dataDir() {
         return PhoneConfig.DIR.resolve("apps").resolve(this.info.id());
      }

      @Override
      public void sound(PhoneSound sound) {
         PhoneOS.get().sound(sound);
      }

      @Override
      public void runOnMain(Runnable task) {
         Minecraft.getInstance().execute(task);
      }
   }

   /** Saves every app's data now (game closing). */
   public void flushAll() {
      for (Running r : this.running.values()) r.context.flush();
   }
}
