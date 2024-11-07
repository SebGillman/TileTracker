package sebgillman.strava_activity_processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PasswordGeneratorTests {

    @Test
    void testGenerateRandomStringLength() {
        int length = 10;
        String randomString = PasswordGenerator.generateRandomString(length);

        assertNotNull(randomString, "Generated string should not be null.");
        assertEquals(length, randomString.length(), "Generated string length should match the requested length.");
    }

    @Test
    void testGenerateRandomStringUniqueness() {
        int length = 20;
        String string1 = PasswordGenerator.generateRandomString(length);
        String string2 = PasswordGenerator.generateRandomString(length);

        assertNotEquals(string1, string2, "Generated strings should be different each time.");
    }

    @Test
    void testGenerateRandomStringContainsValidCharacters() {
        int length = 15;
        String randomString = PasswordGenerator.generateRandomString(length);

        for (char c : randomString.toCharArray()) {
            assertTrue("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".indexOf(c) >= 0,
                    "Generated string contains invalid character: " + c);
        }
    }

    @Test
    void testGenerateRandomStringWithDifferentLengths() {
        String string1 = PasswordGenerator.generateRandomString(10);
        String string2 = PasswordGenerator.generateRandomString(15);

        assertNotEquals(string1.length(), string2.length(), "Strings of different lengths should not be the same length.");
    }
}
