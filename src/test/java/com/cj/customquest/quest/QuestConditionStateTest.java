package com.cj.customquest.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestConditionStateTest {

    @Test
    void onlyReportsTransitionIntoMetState() {
        QuestProgress progress = new QuestProgress(1L);

        assertFalse(progress.updateConditionMet(false));
        assertTrue(progress.updateConditionMet(true));
        assertTrue(progress.isConditionMet());
        assertFalse(progress.updateConditionMet(true));
    }

    @Test
    void losingAndRegainingRequiredItemsDoesNotRepeatTheTransition() {
        QuestProgress progress = new QuestProgress(1L);

        assertTrue(progress.updateConditionMet(true));
        assertFalse(progress.updateConditionMet(false));
        assertTrue(progress.isConditionMet());
        assertFalse(progress.updateConditionMet(true));
    }
}
