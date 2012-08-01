package org.mctourney.AutoReferee;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

import org.mctourney.AutoReferee.AutoReferee.eMatchStatus;

import com.google.common.collect.Maps;

public class PlayerVersusPlayerListener implements Listener
{
	AutoReferee plugin = null;
	public Map<UUID, AutoRefPlayer> tntOwner;

	public PlayerVersusPlayerListener(Plugin p)
	{
		plugin = (AutoReferee) p;
		tntOwner = Maps.newHashMap();
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void playerDeath(PlayerDeathEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match != null)
		{
			// get victim, and killer (maybe null) of this player
			Player victim = (Player) event.getEntity();
			AutoRefPlayer vdata = match.getPlayer(victim);
			
			Player killer = victim.getKiller();
			AutoRefPlayer kdata = match.getPlayer(killer);
			
			String dmsg = event.getDeathMessage();
			EntityDamageEvent lastDmg = victim.getLastDamageCause();

			// if the death was due to intervention by the plugin
			// let's change the death message to reflect this fact
			if (lastDmg == AutoRefPlayer.VOID_DEATH)
			{
				dmsg = victim.getName() + " entered the void lane.";
				event.getDrops().clear();
			}

			// update the death message with the changes
			event.setDeathMessage(dmsg);
			
			// register the death of the victim
			if (match.getCurrentState() == eMatchStatus.PLAYING &&
				vdata != null) vdata.registerDeath(event);
			
			// register the kill for the killer
			if (match.getCurrentState() == eMatchStatus.PLAYING &&
				kdata != null) kdata.registerKill(event);
			
			// now remove the death message (so we can control who receives it)
			event.setDeathMessage(null);
		}
	}

	public static Player entityToPlayer(Entity e)
	{
		// damaging entity is an actual player, easy!
		if ((e instanceof Player)) return (Player) e;

		// damaging entity is an arrow, then who was bow?
		if ((e instanceof Projectile))
		{
			LivingEntity shooter = ((Projectile) e).getShooter();
			if ((shooter instanceof Player)) return (Player) shooter;
		}
		return null;
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void damageDealt(EntityDamageEvent event)
	{
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;

		if ((event instanceof EntityDamageByEntityEvent))
		{
			EntityDamageByEntityEvent ed = (EntityDamageByEntityEvent) event;
			Player damager = entityToPlayer(ed.getDamager());
			Player damaged = entityToPlayer(ed.getEntity());
			
			if (null != damager && match.getCurrentState() == eMatchStatus.PLAYING
				&& ed.getDamager() instanceof Arrow)
			{
				AutoRefPlayer apl = match.getPlayer(damager);
				if (apl != null) ++apl.shotsHit;
			}

			// if either of these aren't players, nothing to do here
			if (null == damager || null == damaged) return;

			// if the damaged entity was a player
			if (null != damaged)
			{
				// if the match is in progress and player is in start region
				// cancel any damage dealt to the player
				if (match.getCurrentState() == eMatchStatus.PLAYING && 
						match.inStartRegion(damaged.getLocation()))
				{ event.setCancelled(true); return; }
			}
			
			// get team affiliations of these players (maybe null)
			AutoRefTeam d1team = plugin.getTeam(damager);
			AutoRefTeam d2team = plugin.getTeam(damaged);
			if (d1team == null && d2team == null) return;

			// if the attacked isn't on a team, or same team (w/ no FF), cancel
			event.setCancelled(d2team == null ||
				(d1team == d2team && match.allowFriendlyFire));

			if (event.isCancelled()) return;
		}

		// save player data if the damaged entity was a player	
		if (event.getEntityType() == EntityType.PLAYER && 
			match.getCurrentState() == eMatchStatus.PLAYING)
		{
			AutoRefPlayer pdata = match.getPlayer((Player) event.getEntity());	
			if (pdata != null) pdata.registerDamage(event);
		}
	}

	@EventHandler
	public void playerBowFire(EntityShootBowEvent event)
	{
		// if the entity is not a player, we don't care
		if (event.getEntityType() != EntityType.PLAYER) return;
		
		Player player = (Player) event.getEntity();
		AutoRefMatch match = plugin.getMatch(player.getWorld());
		if (match == null) return;
		
		AutoRefPlayer apl = match.getPlayer(player);
		if (apl != null) ++apl.shotsFired;
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void explosionPrime(ExplosionPrimeEvent event)
	{
		// TODO: Waiting on BUKKIT-770
		AutoRefMatch match = plugin.getMatch(event.getEntity().getWorld());
		if (match == null) return;
		
		AutoRefPlayer apl = match.getNearestPlayer(event.getEntity().getLocation());
		if (apl == null) return;
	}
}
