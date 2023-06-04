package com.cavetale.overboard;

import com.cavetale.core.struct.Vec3i;

/**
 * Player data stored in Save.
 */
public final class Pirate {
    protected boolean playing;
    protected Vec3i spawnLocation;
    protected int respawnCooldown;
    protected int deaths;

    public Pirate() { }
}
