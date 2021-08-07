package com.cavetale.overboard;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Value;
import org.bukkit.Location;

@Value
public final class Cuboid {
    public static final Cuboid ZERO = new Cuboid(Vec3i.ZERO, Vec3i.ZERO);
    public final Vec3i min;
    public final Vec3i max;

    public boolean contains(int x, int y, int z) {
        return x >= min.x
            && y >= min.y
            && z >= min.z
            && x <= max.x
            && y <= max.y
            && z <= max.z;
    }

    public boolean contains(Vec3i vec) {
        return contains(vec.x, vec.y, vec.z);
    }

    public boolean contains(Location location) {
        return contains(location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ());
    }

    public Vec3i random() {
        Random random = ThreadLocalRandom.current();
        return new Vec3i(min.x + random.nextInt(max.x - min.x + 1),
                         min.y + random.nextInt(max.y - min.y + 1),
                         min.z + random.nextInt(max.z - min.z + 1));
    }

    @Override
    public String toString() {
        return min + "-" + max;
    }
}
