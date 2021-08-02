package de.ambertation.wunderreich.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import de.ambertation.wunderreich.interfaces.BoxOfEirContainerProvider;
import de.ambertation.wunderreich.inventory.BoxOfEirContainer;
import net.minecraft.core.RegistryAccess.RegistryHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerResources;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements BoxOfEirContainerProvider {
	private BoxOfEirContainer boxOfEirContainer;
	
	public BoxOfEirContainer getBoxOfEirContainer(){
		return boxOfEirContainer;
	}
	
	@Inject(method="<init>", at=@At("TAIL"))
	public void wunderreich_init(Thread thread, RegistryHolder registryHolder, LevelStorageAccess levelStorageAccess, WorldData worldData, PackRepository packRepository, Proxy proxy, DataFixer dataFixer, ServerResources serverResources, MinecraftSessionService minecraftSessionService, GameProfileRepository gameProfileRepository, GameProfileCache gameProfileCache, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci){
		boxOfEirContainer = new BoxOfEirContainer();
		boxOfEirContainer.load();
	}

}
