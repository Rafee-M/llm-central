package org.llmcentral.app.ui;

import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.llmcentral.app.provider.ProviderSpec;
import org.llmcentral.app.provider.Providers;

/**
 * Every provider shows its logo in the top bar and the account menu ({@link ProviderBadge#logoFor}); letters are only a
 * fallback. Catches a new provider added without its {@code res/drawable/ic_logo_*.xml} line (summary.md §5.4).
 */
public class ProviderBadgeTest {

    @Test
    public void everyProviderHasALogo() {
        for (ProviderSpec provider : Providers.ALL) {
            assertNotEquals(provider.id + " has no logo in ProviderBadge.logoFor", 0, ProviderBadge.logoFor(provider));
        }
    }
}
