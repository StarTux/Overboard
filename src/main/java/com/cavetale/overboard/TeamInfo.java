package com.cavetale.overboard;

import com.cavetale.area.struct.Area;
import com.cavetale.core.struct.Vec3i;

public final class TeamInfo {
    protected int alive; // temporary cache
    protected Area ship = Area.ZERO;
    protected Area spawn = Area.ZERO;
    protected Vec3i treasure = Vec3i.ZERO;
    protected Area explosive = Area.ZERO;
    protected Area drop = Area.ZERO;
}
