package org.example.hanjinwoo.gourmet2.entity;

/**
 * A mob that fights with the same systems a player does. It owns a {@link MobFighter}, whose stand-in player runs the
 * mod's skill, combat, leap and terrain systems unchanged; the only thing the mob supplies itself is how a move
 * looks, since those systems name their animations by clip and a mob has no Epic Fight rig to play them on.
 */
public interface ClipPlayer {
    /** The stand-in and its inputs. */
    MobFighter fighter();

    /** Plays the animation for a clip the player systems asked for (basic1, nail_punch_charge, ...). */
    void playClip(String clip);
}
