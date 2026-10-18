package com.healthsystem.service.impl;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ManagementFocusPolicyTest {
    @Test void fusionUsesUnroundedContributionsAndDocumentRoundingRuleInsteadOfInconsistentThreeDiseaseExample() {
        // beta-cell / morning-surge / post-stroke, priority 1/2/3. Exact unrounded percentages:
        // 20, 13 1/3, 7.5, 8 1/3, 23 1/3, 20, 7.5. Rounded total 99.9.
        // Largest unrounded weight is monitoring: it receives +0.1, not TCM.
        int[] actual=ManagementFocusPolicy.roundToBps(new long[]{12000,8000,4500,5000,14000,12000,4500},6);
        assertThat(actual).containsExactly(2000,1330,750,830,2340,2000,750);
        assertThat(Arrays.stream(actual).sum()).isEqualTo(10000);
        assertThat(ManagementFocusPolicy.roundToBps(new long[]{10000,20000,30000},6)).containsExactly(1670,3330,5000);
    }
}
