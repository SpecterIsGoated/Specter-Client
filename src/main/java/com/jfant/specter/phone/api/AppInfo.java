package com.jfant.specter.phone.api;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Everything the phone needs to know about an app before it runs: who it is, how its icon looks, what it may use.
 * Build one with {@link #builder(String)}; register it with its factory through {@code PhoneApps.register}.
 *
 * @param id          unique, lower case ({@code donutgram}, {@code mymod_weather}); also names the app's data file
 * @param order       position on the home screen (lower first); apps with equal order sort by name
 * @param system      a phone component (Settings, Chat): always present, cannot be hidden
 * @param background  receives {@link PhoneApplication#backgroundTick()} even while the phone is closed
 * @param accent      the app's own colour (switcher card, notification stripe)
 */
public record AppInfo(
   String id,
   String name,
   String description,
   String version,
   AppCategory category,
   AppIcon icon,
   Set<AppPermission> permissions,
   int order,
   boolean system,
   boolean background,
   int accent
) {
   private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,40}");

   public AppInfo {
      Objects.requireNonNull(id, "id");
      if (!ID.matcher(id).matches()) throw new IllegalArgumentException("App id must be 1-40 of a-z 0-9 _ . - : " + id);
      Objects.requireNonNull(name, "name");
      description = description == null ? "" : description;
      version = version == null ? "1.0" : version;
      category = category == null ? AppCategory.UTILITIES : category;
      Objects.requireNonNull(icon, "icon");
      permissions = permissions == null || permissions.isEmpty()
         ? Collections.emptySet()
         : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
   }

   public boolean may(AppPermission permission) {
      return this.permissions.contains(permission);
   }

   public static Builder builder(String id) {
      return new Builder(id);
   }

   public static final class Builder {
      private final String id;
      private String name;
      private String description = "";
      private String version = "1.0";
      private AppCategory category = AppCategory.UTILITIES;
      private AppIcon icon;
      private final EnumSet<AppPermission> permissions = EnumSet.noneOf(AppPermission.class);
      private int order = 500;
      private boolean system;
      private boolean background;
      private int accent = 0xFF8B5CF6;

      private Builder(String id) {
         this.id = id;
         this.name = id;
      }

      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder description(String description) {
         this.description = description;
         return this;
      }

      public Builder version(String version) {
         this.version = version;
         return this;
      }

      public Builder category(AppCategory category) {
         this.category = category;
         return this;
      }

      public Builder icon(AppIcon icon) {
         this.icon = icon;
         return this;
      }

      public Builder permissions(AppPermission... permissions) {
         Collections.addAll(this.permissions, permissions);
         return this;
      }

      public Builder order(int order) {
         this.order = order;
         return this;
      }

      public Builder system() {
         this.system = true;
         return this;
      }

      public Builder background() {
         this.background = true;
         return this;
      }

      public Builder accent(int argb) {
         this.accent = argb;
         return this;
      }

      public AppInfo build() {
         return new AppInfo(this.id, this.name, this.description, this.version, this.category, this.icon, this.permissions, this.order,
            this.system, this.background, this.accent);
      }
   }
}
