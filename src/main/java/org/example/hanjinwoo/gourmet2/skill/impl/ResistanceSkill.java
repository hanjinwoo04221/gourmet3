package org.example.hanjinwoo.gourmet2.skill.impl;

import net.minecraft.sounds.SoundEvents;
import org.example.hanjinwoo.gourmet2.skill.Hurt;
import org.example.hanjinwoo.gourmet2.skill.ResistanceEngine;
import org.example.hanjinwoo.gourmet2.skill.SkillBehavior;
import org.example.hanjinwoo.gourmet2.skill.SkillContext;

/**
 * 抵抗 Resistance — the active half. Braces the body for a moment: blows that land in the window are shrugged off up
 * to a pool of damage set by the Gourmet Cell level, and a blow the pool covers well enough throws its attacker off
 * and cuts their combo short. See {@link ResistanceEngine}. (The passive half needs no key: it is always on.)
 */
public class ResistanceSkill implements SkillBehavior {
    @Override
    public boolean activate(SkillContext ctx) {
        ResistanceEngine.open(ctx.player(), ctx.data());
        Hurt.playSound(ctx, ctx.player().position(), SoundEvents.IRON_GOLEM_REPAIR, 0.8F, 1.3F);
        return true;
    }
}
