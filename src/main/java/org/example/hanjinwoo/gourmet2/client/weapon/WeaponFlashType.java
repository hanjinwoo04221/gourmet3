package org.example.hanjinwoo.gourmet2.client.weapon;

import net.minecraft.util.FastColor;

/** Which cutlery shape flashes over the caster's limb, and what color it glows. */
public enum WeaponFlashType {
    /** 못(釘) — Nail Punch / Nail Gun / 13-hit Nail Punch. */
    NAIL(FastColor.ARGB32.color(255, 255, 210, 75)),
    /** 포크 — Fork / Flying Fork. */
    FORK(FastColor.ARGB32.color(255, 127, 227, 200)),
    /** 나이프 — Knife / Leg Knife / Flying Knife. */
    KNIFE(FastColor.ARGB32.color(255, 200, 242, 255));

    private final int tint;

    WeaponFlashType(int tint) {
        this.tint = tint;
    }

    /** Opaque ARGB tint applied to the flash model. */
    public int tint() {
        return tint;
    }
}
