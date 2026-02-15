package net.serverplugins.items.models;

import org.bukkit.Instrument;

public record BlockStateMapping(String itemId, Instrument instrument, int note, boolean powered) {

    public String toBlockDataString() {
        return "minecraft:note_block[instrument="
                + instrument.name().toLowerCase()
                + ",note="
                + note
                + ",powered="
                + powered
                + "]";
    }
}
