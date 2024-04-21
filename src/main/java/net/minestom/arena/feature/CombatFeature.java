package net.minestom.arena.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.EntityDamage;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.MathUtils;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToLongFunction;

/**
 * @param playerCombat Allow player combat
 * @param damageFunction Uses the return value as damage to apply (in lambda arg 1 is attacker, arg 2 is victim)
 * @param invulnerabilityFunction Uses the return value as time an entity is invulnerable after getting attacked (in lambda arg 1 is victim)
 */
record CombatFeature(boolean playerCombat, ToDoubleBiFunction<Entity, Entity> damageFunction, ToLongFunction<Entity> invulnerabilityFunction) implements Feature {
    private static final Tag<Long> INVULNERABLE_UNTIL_TAG = Tag.Long("invulnerable_until").defaultValue(0L);

    @Override
    public void hook(@NotNull EventNode<InstanceEvent> node) {
        node.addListener(ProjectileCollideWithEntityEvent.class, event -> {
            if (!(event.getTarget() instanceof LivingEntity target)) return;
            if (!(event.getEntity() instanceof EntityProjectile projectile)) return;

            // PVP is disabled and two players have attempted to hit each other
            Entity shooter = projectile.getShooter();
            if (!playerCombat && target instanceof Player && shooter instanceof Player) return;

            // Don't apply damage if entity is invulnerable
            final long now = System.currentTimeMillis();
            final long invulnerableUntil = target.getTag(INVULNERABLE_UNTIL_TAG);
            if (invulnerableUntil > now) return;

            float damageFloat = (float) damageFunction.applyAsDouble(projectile, target);
            Damage damage = EntityDamage.fromProjectile(shooter, projectile, damageFloat);

            target.damage(damage);
            target.setTag(INVULNERABLE_UNTIL_TAG, now + invulnerabilityFunction.applyAsLong(target));

            takeKnockbackFromArrow(target, projectile);
            if (damageFloat > 0) spawnHologram(target, damageFloat);

            projectile.remove();
        }).addListener(EntityAttackEvent.class, event -> {
            if (!(event.getTarget() instanceof LivingEntity target)) return;

            // PVP is disabled and two players have attempted to hit each other
            if (!playerCombat && target instanceof Player && event.getEntity() instanceof Player) return;

            // Can't have dead sources attacking things
            if (((LivingEntity) event.getEntity()).isDead()) return;

            // Don't apply damage if entity is invulnerable
            final long now = System.currentTimeMillis();
            final long invulnerableUntil = target.getTag(INVULNERABLE_UNTIL_TAG);
            if (invulnerableUntil > now) return;

            float damageFloat = (float) damageFunction.applyAsDouble(event.getEntity(), target);
            Damage damage = EntityDamage.fromEntity(event.getEntity(), damageFloat);

            target.damage(damage);
            target.setTag(INVULNERABLE_UNTIL_TAG, now + invulnerabilityFunction.applyAsLong(target));

            takeKnockback(target, event.getEntity());
            if (damageFloat > 0) spawnHologram(target, damageFloat);
        });
    }

    private static void takeKnockback(Entity target, Entity source) {
        target.takeKnockback(
                0.3f,
                Math.sin(source.getPosition().yaw() * (Math.PI / 180)),
                -Math.cos(source.getPosition().yaw() * (Math.PI / 180))
        );
    }

    private static void takeKnockbackFromArrow(Entity target, EntityProjectile source) {
        if (source.getShooter() == null) return;
        takeKnockback(target, source.getShooter());
    }

    private static void spawnHologram(Entity target, float damage) {
        damage = MathUtils.round(damage, 2);

        Entity entity = new Entity(EntityType.TEXT_DISPLAY);
        TextDisplayMeta entityMeta = (TextDisplayMeta) entity.getEntityMeta();
        entity.setInstance(
                target.getInstance(),
                target.getPosition().add(0, target.getEyeHeight(), 0)
        );
        entityMeta.setHasNoGravity(false);
        entityMeta.setText(
                Component.text(damage, NamedTextColor.RED)
        );

        // Velocity
        Random random = ThreadLocalRandom.current();
        entity.setVelocity(entity.getPosition()
                .direction()
                .withX(random.nextDouble(2))
                .withY(3)
                .withZ(random.nextDouble(2))
                .normalize().mul(3));

        entity.scheduleRemove(Duration.of(15, TimeUnit.SERVER_TICK));
    }
}
