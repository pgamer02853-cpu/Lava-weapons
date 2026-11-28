package com.rizgamer.lavaweapons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Turns Paper's "update-equipment-on-player-actions" off so attribute swapping (lunge swap etc.) works. */
public final class SwapFix {
    private static final String KEY = "update-equipment-on-player-actions";
    private SwapFix() {}

    private static Path file() { return Path.of("config", "paper-global.yml"); }

    /** 1 = swapping blocked, 0 = swapping allowed, -1 = key not found / file unreadable */
    public static int state() {
        try {
            for (String line : Files.readAllLines(file())) {
                String t = line.trim();
                if (t.startsWith(KEY + ":")) return t.substring(KEY.length() + 1).trim().startsWith("true") ? 1 : 0;
            }
        } catch (IOException ignored) { }
        return -1;
    }

    /** returns DONE, ALREADY, NOTFOUND or ERROR:... */
    public static String fix() {
        Path f = file();
        try {
            List<String> lines = Files.readAllLines(f);
            boolean found = false, changed = false;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith(KEY + ":")) {
                    found = true;
                    if (t.substring(KEY.length() + 1).trim().startsWith("true")) {
                        lines.set(i, lines.get(i).replace("true", "false"));
                        changed = true;
                    }
                }
            }
            if (!found) return "NOTFOUND";
            if (!changed) return "ALREADY";
            Files.copy(f, Path.of("config", "paper-global.yml.bak"), StandardCopyOption.REPLACE_EXISTING);
            Files.write(f, lines);
            return "DONE";
        } catch (IOException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
