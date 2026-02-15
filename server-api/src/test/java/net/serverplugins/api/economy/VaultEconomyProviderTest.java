package net.serverplugins.api.economy;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VaultEconomyProviderTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("TestPlayer");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPlayerInstanceNotNull() {
        assertNotNull(player, "Test player should be created");
        assertTrue(player.isOnline(), "Test player should be online");
    }

    @Test
    void testPlayerHasUUID() {
        assertNotNull(player.getUniqueId(), "Player should have a UUID");
    }

    @Test
    void testPlayerHasName() {
        assertEquals("TestPlayer", player.getName(), "Player name should match");
    }
}
