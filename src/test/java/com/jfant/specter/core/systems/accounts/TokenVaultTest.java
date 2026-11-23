package com.jfant.specter.core.systems.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenVaultTest {
   @Test
   void roundTripAndNeverPlain() {
      String token = "eyJhbGciOiJIUzI1NiJ9.example-token-value";
      String stored = TokenVault.protect(token);
      assertTrue(TokenVault.isProtected(stored));
      assertFalse(stored.contains("example-token-value"));
      assertEquals(token, TokenVault.reveal(stored));
      // random IV: the same token never encrypts the same way twice
      assertNotEquals(stored, TokenVault.protect(token));
   }

   @Test
   void legacyPlainValuesStillLoad() {
      assertEquals("old-plain-token", TokenVault.reveal("old-plain-token"));
      assertEquals("", TokenVault.reveal(null));
   }

   @Test
   void tamperedValueRevealsNothing() {
      String stored = TokenVault.protect("secret");
      char[] c = stored.toCharArray();
      c[20] = c[20] == 'A' ? 'B' : 'A';
      assertEquals("", TokenVault.reveal(new String(c)));
   }
}
