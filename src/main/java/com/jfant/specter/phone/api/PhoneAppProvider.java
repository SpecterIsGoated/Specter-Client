package com.jfant.specter.phone.api;

import java.util.function.Supplier;

/**
 * For other mods: list a class implementing this under the {@code specter:phone} entrypoint in your
 * {@code fabric.mod.json} and the phone finds your apps at start-up, no changes to Specter needed:
 *
 * <pre>{@code
 * "entrypoints": { "specter:phone": ["com.example.MyPhoneApps"] }
 *
 * public class MyPhoneApps implements PhoneAppProvider {
 *    public void registerApps(Registrar apps) {
 *       apps.register(AppInfo.builder("example_weather").name("Weather").icon(...).build(), WeatherApp::new);
 *    }
 * }
 * }</pre>
 */
public interface PhoneAppProvider {
   void registerApps(Registrar apps);

   @FunctionalInterface
   interface Registrar {
      void register(AppInfo info, Supplier<? extends PhoneApplication> factory);
   }
}
