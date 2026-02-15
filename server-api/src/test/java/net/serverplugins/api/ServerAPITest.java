package net.serverplugins.api;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerAPITest {

    private ServerMock server;
    private ServerAPI plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ServerAPI.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPluginEnables() {
        assertTrue(plugin.isEnabled(), "ServerAPI plugin should be enabled");
    }

    @Test
    void testPluginInstanceNotNull() {
        assertNotNull(ServerAPI.getInstance(), "ServerAPI instance should not be null");
    }

    @Test
    void testDatabaseInitializes() {
        assertNotNull(plugin.getDatabase(), "Database should be initialized");
    }

    @Test
    void testConfigNotNull() {
        assertNotNull(plugin.getConfig(), "Config should not be null");
    }
}
