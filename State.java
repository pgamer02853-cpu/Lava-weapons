package com.rizgamer.lavaweapons;

import java.util.*;

/** Runtime flags. Main thread only. */
public final class State {
    public final Map<UUID, Long> shield = new HashMap<>();
    public final Map<UUID, Long> noFall = new HashMap<>();
    public final Map<UUID, Long> blastImmune = new HashMap<>();
    public final Map<UUID, Long> slamPending = new HashMap<>();
    public final Set<UUID> locked = new HashSet<>();

    public boolean active(Map<UUID, Long> m, UUID id) {
        Long t = m.get(id);
        return t != null && t > System.currentTimeMillis();
    }
    public void put(Map<UUID, Long> m, UUID id, long millis) { m.put(id, System.currentTimeMillis() + millis); }
}
