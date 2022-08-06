package com.cavetale.overboard;

import com.cavetale.core.struct.Vec3i;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor @NoArgsConstructor
public final class Cannon {
    protected Vec3i vector;
    protected int countdown;
    protected UUID armorStand;
}
