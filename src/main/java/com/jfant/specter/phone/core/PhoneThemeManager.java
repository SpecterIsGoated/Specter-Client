package com.jfant.specter.phone.core;

import java.util.ArrayList;
import java.util.List;

/** The active theme and wallpaper. New themes are added with {@link #add} and appear in Settings by themselves. */
public final class PhoneThemeManager {
   private final PhoneConfig config;
   private final List<PhoneTheme> themes = new ArrayList<>(List.of(PhoneTheme.MIDNIGHT, PhoneTheme.DAYLIGHT, PhoneTheme.FROSTING));
   private PhoneTheme current;

   public PhoneThemeManager(PhoneConfig config) {
      this.config = config;
      this.current = this.find(config.theme);
   }

   public PhoneTheme theme() {
      return this.current;
   }

   public List<PhoneTheme> themes() {
      return this.themes;
   }

   public void add(PhoneTheme theme) {
      this.themes.removeIf(t -> t.id().equals(theme.id()));
      this.themes.add(theme);
   }

   public void set(String id) {
      this.current = this.find(id);
      this.config.theme = this.current.id();
      this.config.markDirty();
   }

   /** The wallpaper in use: the one picked in Settings, or the theme's own. */
   public String wallpaper() {
      return this.config.wallpaper != null ? this.config.wallpaper : this.current.wallpaper();
   }

   public void setWallpaper(String id) {
      this.config.wallpaper = id;
      this.config.markDirty();
   }

   private PhoneTheme find(String id) {
      for (PhoneTheme t : this.themes) if (t.id().equals(id)) return t;
      return PhoneTheme.MIDNIGHT;
   }
}
