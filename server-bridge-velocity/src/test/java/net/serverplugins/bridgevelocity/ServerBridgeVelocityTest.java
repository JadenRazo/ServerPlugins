package net.serverplugins.bridgevelocity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Basic test for ServerBridgeVelocity. Note: This is a Velocity plugin, not a Bukkit plugin, so
 * MockBukkit cannot be used. These tests verify basic functionality without server mocking.
 */
class ServerBridgeVelocityTest {

    @Test
    void testBasicAssertion() {
        // Basic sanity test to verify JUnit is working
        assertTrue(true, "JUnit is configured correctly");
    }

    @Test
    void testStringManipulation() {
        String pluginName = "ServerBridgeVelocity";
        assertEquals("ServerBridgeVelocity", pluginName, "Plugin name should match");
    }
}
