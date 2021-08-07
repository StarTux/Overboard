package com.cavetale.overboard;

/**
 * Player data stored in Save.
 */
public final class Pirate {
    protected Team team;
    protected Vec3i bed1;
    protected Vec3i bed2;
    protected int respawnCooldown;

    public Pirate() { }

    public Pirate(final Team team) {
        this.team = team;
    }
}
