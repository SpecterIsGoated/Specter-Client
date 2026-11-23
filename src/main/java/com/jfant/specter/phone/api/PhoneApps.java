package com.jfant.specter.phone.api;

import com.jfant.specter.phone.PhoneOS;
import java.util.function.Supplier;

/**
 * Registering an app: {@code PhoneApps.register(info, MyApp::new)}. That is all - the icon, name, opening, closing,
 * navigation, notifications and storage are handled by the phone. Safe to call any time after Specter starts.
 */
public final class PhoneApps {
   private PhoneApps() {
   }

   public static boolean register(AppInfo info, Supplier<? extends PhoneApplication> factory) {
      return PhoneOS.get().registry().register(info, factory, callerName());
   }

   private static String callerName() {
      return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(s -> s.skip(2).findFirst().map(f -> f.getClassName()).orElse("?"));
   }
}
