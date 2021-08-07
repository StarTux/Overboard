package com.cavetale.overboard;

public final class TeamInfo {
    int alive; // temporary cache
    Cuboid ship = Cuboid.ZERO;
    Cuboid spawn = Cuboid.ZERO;
    Vec3i treasure = Vec3i.ZERO;
    Cuboid explosive = Cuboid.ZERO;
    Cuboid drop = Cuboid.ZERO;
}
