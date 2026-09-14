package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GPermissionsTest
{

    @Test
    void parseFullPermissions()
    {
        GPermissions perms = new GPermissions("-rwxrwxrwx");

        assertTrue(perms.getOwnerHasRead());
        assertTrue(perms.getOwnerHasWrite());
        assertTrue(perms.getOwnerHasExecute());
        assertTrue(perms.getGroupHasRead());
        assertTrue(perms.getGroupHasWrite());
        assertTrue(perms.getGroupHasExecute());
        assertTrue(perms.getWorldHasRead());
        assertTrue(perms.getWorldHasWrite());
        assertTrue(perms.getWorldHasExecute());
        assertEquals(777, perms.getNumericValue());
        assertEquals("-", perms.getStartChar());
    }

    @Test
    void parseNoPermissions()
    {
        GPermissions perms = new GPermissions("----------");

        assertFalse(perms.getOwnerHasRead());
        assertFalse(perms.getOwnerHasWrite());
        assertFalse(perms.getOwnerHasExecute());
        assertFalse(perms.getGroupHasRead());
        assertFalse(perms.getGroupHasWrite());
        assertFalse(perms.getGroupHasExecute());
        assertFalse(perms.getWorldHasRead());
        assertFalse(perms.getWorldHasWrite());
        assertFalse(perms.getWorldHasExecute());
        assertEquals(0, perms.getNumericValue());
    }

    @Test
    void parseDirectory755()
    {
        GPermissions perms = new GPermissions("drwxr-xr-x");

        assertEquals("d", perms.getStartChar());
        assertEquals(755, perms.getNumericValue());
        assertTrue(perms.getOwnerHasRead());
        assertTrue(perms.getOwnerHasWrite());
        assertTrue(perms.getOwnerHasExecute());
        assertTrue(perms.getGroupHasRead());
        assertFalse(perms.getGroupHasWrite());
        assertTrue(perms.getGroupHasExecute());
        assertTrue(perms.getWorldHasRead());
        assertFalse(perms.getWorldHasWrite());
        assertTrue(perms.getWorldHasExecute());
    }

    @Test
    void parseSymlink()
    {
        GPermissions perms = new GPermissions("lrwxrwxrwx");

        assertEquals("l", perms.getStartChar());
        assertEquals(777, perms.getNumericValue());
    }

    @Test
    void parseCommon644()
    {
        GPermissions perms = new GPermissions("-rw-r--r--");

        assertEquals(644, perms.getNumericValue());
        assertTrue(perms.getOwnerHasRead());
        assertTrue(perms.getOwnerHasWrite());
        assertFalse(perms.getOwnerHasExecute());
        assertTrue(perms.getGroupHasRead());
        assertFalse(perms.getGroupHasWrite());
        assertFalse(perms.getGroupHasExecute());
        assertTrue(perms.getWorldHasRead());
        assertFalse(perms.getWorldHasWrite());
        assertFalse(perms.getWorldHasExecute());
    }

    @Test
    void octalConstructorRoundTrip()
    {
        // Parse a string, then reconstruct from bitfield + startChar
        GPermissions original = new GPermissions("drwxr-xr-x");
        GPermissions rebuilt  = new GPermissions(original.getBitField(), original.getStartChar());

        assertEquals(original.getNumericValue(), rebuilt.getNumericValue());
        assertEquals(original.getStartChar(), rebuilt.getStartChar());
        assertEquals(original.getSymbolicValue(), rebuilt.getSymbolicValue());
        assertEquals(original.getBitField(), rebuilt.getBitField());
    }

    @Test
    void copyConstructorIndependence()
    {
        GPermissions original = new GPermissions("-rwxr-xr-x");
        GPermissions copy     = new GPermissions(original);

        assertEquals(original.getNumericValue(), copy.getNumericValue());
        assertEquals(original.getSymbolicValue(), copy.getSymbolicValue());

        // Mutate the copy; original must not change
        copy.twiddleOwnerRead();
        assertTrue(original.getOwnerHasRead(), "Original should be unaffected by copy mutation");
        assertFalse(copy.getOwnerHasRead(), "Copy should have toggled owner read off");
        assertNotEquals(original.getNumericValue(), copy.getNumericValue());
    }

    @Test
    void twiddleTogglesAllNineBits()
    {
        GPermissions perms = new GPermissions("-rwxrwxrwx");

        // Toggle every bit off
        perms.twiddleOwnerRead();
        perms.twiddleOwnerWrite();
        perms.twiddleOwnerExecute();
        perms.twiddleGroupRead();
        perms.twiddleGroupWrite();
        perms.twiddleGroupExecute();
        perms.twiddleWorldRead();
        perms.twiddleWorldWrite();
        perms.twiddleWorldExecute();

        assertEquals(0, perms.getNumericValue());
        assertFalse(perms.getOwnerHasRead());
        assertFalse(perms.getWorldHasExecute());

        // Toggle them all back on
        perms.twiddleOwnerRead();
        perms.twiddleOwnerWrite();
        perms.twiddleOwnerExecute();
        perms.twiddleGroupRead();
        perms.twiddleGroupWrite();
        perms.twiddleGroupExecute();
        perms.twiddleWorldRead();
        perms.twiddleWorldWrite();
        perms.twiddleWorldExecute();

        assertEquals(777, perms.getNumericValue());
    }

    @Test
    void symbolicValueRoundTrip()
    {
        String[] samples = {
            "-rwxrwxrwx",
            "----------",
            "-rw-r--r--",
            "drwxr-xr-x",
            "-rwx------",
            "lrwxrwxrwx"
        };

        for (String sample : samples)
        {
            GPermissions perms = new GPermissions(sample);
            assertEquals(sample, perms.getSymbolicValue(),
                "Symbolic round-trip failed for: " + sample);
        }
    }

    @Test
    void numericValueAsStringFormatting()
    {
        // 755 - owner digit is non-zero, no leading zero needed
        GPermissions p755 = new GPermissions("drwxr-xr-x");
        assertEquals("755", p755.getNumericValueAsString());

        // 644
        GPermissions p644 = new GPermissions("-rw-r--r--");
        assertEquals("644", p644.getNumericValueAsString());

        // 000 - owner has nothing, so a leading zero is prepended
        GPermissions p000 = new GPermissions("----------");
        assertEquals("00", p000.getNumericValueAsString());

        // 777
        GPermissions p777 = new GPermissions("-rwxrwxrwx");
        assertEquals("777", p777.getNumericValueAsString());
    }
}
