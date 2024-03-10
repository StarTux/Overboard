package com.cavetale.overboard;

import com.cavetale.core.struct.Vec3i;
import java.util.UUID;

/**
 * Player data stored in Save.
 */
public final class Pirate {
    protected UUID uuid;
    protected String name;
    protected boolean playing;
    protected Vec3i spawnLocation;
    protected int respawnCooldown;
    protected int deaths;
    protected PirateTeam team;
    protected boolean dead;
    protected int money;

    public Pirate() { }
}
