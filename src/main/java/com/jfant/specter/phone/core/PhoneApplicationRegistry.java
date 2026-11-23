package com.jfant.specter.phone.core;

import com.jfant.specter.SpecterClientAddon;
import com.jfant.specter.phone.api.AppInfo;
import com.jfant.specter.phone.api.PhoneApplication;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Every app the phone knows about: its {@link AppInfo} and how to make it. Apps are created lazily the first time
 * they open, so registering costs nothing. The home screen, Settings and the switcher all read from here, so a newly
 * registered app shows up everywhere without any other change.
 */
public final class PhoneApplicationRegistry {
   public record Entry(AppInfo info, Supplier<? extends PhoneApplication> factory, String source) {
   }

   private final Map<String, Entry> entries = new LinkedHashMap<>();
   private int version;
   private List<Entry> sorted = List.of();

   public synchronized boolean register(AppInfo info, Supplier<? extends PhoneApplication> factory, String source) {
      if (this.entries.containsKey(info.id())) {
         SpecterClientAddon.LOG.warn("Donut Phone: app id '{}' from {} is already taken by {}; ignored", info.id(), source, this.entries.get(info.id()).source());
         return false;
      }
      this.entries.put(info.id(), new Entry(info, factory, source));
      this.version++;
      List<Entry> list = new ArrayList<>(this.entries.values());
      list.sort(Comparator.comparingInt((Entry e) -> e.info().order()).thenComparing(e -> e.info().name().toLowerCase(java.util.Locale.ROOT)));
      this.sorted = List.copyOf(list);
      return true;
   }

   @Nullable
   public synchronized Entry get(String id) {
      return this.entries.get(id);
   }

   /** All apps in home-screen order. */
   public synchronized List<Entry> all() {
      return this.sorted;
   }

   /** Changes whenever an app is added (the home screen re-lays itself out). */
   public synchronized int version() {
      return this.version;
   }
}
