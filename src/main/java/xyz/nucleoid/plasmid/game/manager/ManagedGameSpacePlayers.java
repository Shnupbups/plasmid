package xyz.nucleoid.plasmid.game.manager;

import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameResult;
import xyz.nucleoid.plasmid.game.GameSpacePlayers;
import xyz.nucleoid.plasmid.game.GameTexts;
import xyz.nucleoid.plasmid.game.player.MutablePlayerSet;
import xyz.nucleoid.plasmid.game.player.PlayerOffer;
import xyz.nucleoid.plasmid.game.player.isolation.IsolatingPlayerTeleporter;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

public final class ManagedGameSpacePlayers implements GameSpacePlayers {
    private final ManagedGameSpace space;
    final MutablePlayerSet set;
    final IsolatingPlayerTeleporter teleporter;

    ManagedGameSpacePlayers(ManagedGameSpace space) {
        this.space = space;
        this.set = new MutablePlayerSet(space.getServer());
        this.teleporter = new IsolatingPlayerTeleporter(space.getServer());
    }

    @Override
    public GameResult screenJoins(Collection<ServerPlayerEntity> players) {
        return this.space.screenJoins(players);
    }

    @Override
    public GameResult offer(ServerPlayerEntity player) {
        if (this.set.contains(player)) {
            return GameResult.error(GameTexts.Join.alreadyJoined());
        }

        var offer = new PlayerOffer(player);
        var result = this.space.offerPlayer(offer);

        var reject = result.asReject();
        if (reject != null) {
            return GameResult.error(reject.reason());
        }

        var accept = result.asAccept();
        if (accept != null) {
            try {
                this.teleporter.teleportIn(player, accept::applyJoin);
                this.set.add(player);
                this.space.onAddPlayer(player);

                return GameResult.ok();
            } catch (Throwable throwable) {
                return GameResult.error(GameTexts.Join.unexpectedError());
            }
        } else {
            return GameResult.error(GameTexts.Join.genericError());
        }
    }

    @Override
    public boolean kick(ServerPlayerEntity player) {
        if (this.remove(player)) {
            this.teleporter.teleportOut(player);
            return true;
        } else {
            return false;
        }
    }

    public boolean remove(ServerPlayerEntity player) {
        if (!this.set.contains(player)) {
            return false;
        }

        this.space.onPlayerRemove(player);

        this.set.remove(player);

        if (this.set.isEmpty()) {
            this.space.close(GameCloseReason.GARBAGE_COLLECTED);
        }

        return true;
    }

    void clear() {
        this.set.clear();
    }

    @Override
    public boolean contains(UUID id) {
        return this.set.contains(id);
    }

    @Override
    @Nullable
    public ServerPlayerEntity getEntity(UUID id) {
        return this.set.getEntity(id);
    }

    @Override
    public int size() {
        return this.set.size();
    }

    @Override
    public Iterator<ServerPlayerEntity> iterator() {
        return this.set.iterator();
    }

    public IsolatingPlayerTeleporter getTeleporter() {
        return this.teleporter;
    }
}
