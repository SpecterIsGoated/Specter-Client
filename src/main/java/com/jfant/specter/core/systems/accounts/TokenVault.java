package com.jfant.specter.core.systems.accounts;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Keeps account tokens encrypted on disk (AES-256-GCM). The key is 32 random bytes in {@code ~/.specter/vault.key} -
 * in the user's home folder, NOT the game folder - so a shared or uploaded {@code .minecraft}/config folder does not
 * carry usable tokens. It does not stop malware already running as you (nothing in a mod can).
 *
 * <p>Stored values look like {@code aes:<base64 iv+ciphertext>}. Anything without the prefix is a token saved by an
 * older version and is read as-is (and re-saved encrypted). Tokens are never logged, here or by callers.
 */
public final class TokenVault {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String PREFIX = "aes:";
   private static final SecureRandom RANDOM = new SecureRandom();
   private static byte[] key;

   private TokenVault() {
   }

   private static synchronized byte[] key() throws IOException {
      if (key != null) return key;
      Path dir = Path.of(System.getProperty("user.home"), ".specter");
      Path file = dir.resolve("vault.key");
      if (Files.isRegularFile(file) && Files.size(file) == 32) {
         key = Files.readAllBytes(file);
         return key;
      }
      Files.createDirectories(dir);
      byte[] fresh = new byte[32];
      RANDOM.nextBytes(fresh);
      Files.write(file, fresh);
      try {
         Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException | IOException ignored) {
         // Windows: the file is inside the user's own profile folder, which only they (and admins) can read
      }
      key = fresh;
      return key;
   }

   public static boolean isProtected(String stored) {
      return stored != null && stored.startsWith(PREFIX);
   }

   /** Encrypts for saving. Returns the plain value only if encryption is impossible (a warning is logged, no token). */
   public static String protect(String plain) {
      if (plain == null || plain.isEmpty() || isProtected(plain)) return plain;
      try {
         byte[] iv = new byte[12];
         RANDOM.nextBytes(iv);
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, iv));
         byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
         byte[] out = new byte[iv.length + body.length];
         System.arraycopy(iv, 0, out, 0, iv.length);
         System.arraycopy(body, 0, out, iv.length, body.length);
         return PREFIX + Base64.getEncoder().encodeToString(out);
      } catch (Exception e) {
         LOG.warn("Could not encrypt an account token ({}); it is saved unencrypted.", e.getClass().getSimpleName());
         return plain;
      }
   }

   /** Decrypts a saved value; legacy plain values pass through. Returns "" when it cannot be decrypted (key lost). */
   public static String reveal(String stored) {
      if (!isProtected(stored)) return stored == null ? "" : stored;
      try {
         byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(128, all, 0, 12));
         return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
      } catch (Exception e) {
         LOG.warn("Could not decrypt a saved account token ({}); log in to that account again.", e.getClass().getSimpleName());
         return "";
      }
   }
}
