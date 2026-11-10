package com.skyrealms.pets.module;

import com.skyrealms.pets.catalog.PetCatalog;
import com.skyrealms.pets.command.PetsCommand;
import com.skyrealms.pets.gui.PetsGuiListener;
import com.skyrealms.pets.listener.PetConnectionListener;
import com.skyrealms.pets.repository.PetRepository;
import com.skyrealms.pets.service.PetEntityManager;
import com.skyrealms.pets.service.PetService;
import com.skyrealms.platform.api.module.ModuleContext;
import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.ModuleMetadata;
import com.skyrealms.platform.api.module.ModuleState;
import com.skyrealms.platform.api.module.SkyModule;
import com.skyrealms.platform.common.command.DynamicCommandRegistrar;
import com.skyrealms.platform.database.DatabaseService;
import com.skyrealms.platform.integrations.PetStatProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * SkyRealms Pets module managing the 8 Cubees ModelEngine companions,
 * custom Pets GUI, leveling, and Garden perk bonuses.
 */
public final class PetsModule implements SkyModule {

    private static final ModuleId ID = ModuleId.of("pets");

    private final Plugin plugin;
    private final ModuleMetadata metadata = ModuleMetadata.builder(ID)
            .name("SkyRealms Pets")
            .version("1.0.0")
            .dependsOn(ModuleId.of("core"))
            .dependsOn(ModuleId.of("database"))
            .build();

    private ModuleState state = ModuleState.UNLOADED;
    private PetService petService;

    public PetsModule(@NotNull Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    @Override
    @NotNull
    public ModuleId id() {
        return ID;
    }

    @Override
    @NotNull
    public ModuleMetadata metadata() {
        return metadata;
    }

    @Override
    @NotNull
    public ModuleState state() {
        return state;
    }

    @Override
    public void onEnable(@NotNull ModuleContext context) throws Exception {
        state = ModuleState.INITIALIZING;

        DatabaseService databaseService = context.services().get(DatabaseService.class);
        PetCatalog catalog = new PetCatalog();
        PetRepository repository = new PetRepository(databaseService);
        PetEntityManager entityManager = new PetEntityManager(plugin);
        this.petService = new PetService(plugin, catalog, repository, entityManager);

        entityManager.start();

        context.services().register(PetService.class, petService);
        context.services().register(PetStatProvider.class, petService);
        Bukkit.getServicesManager().register(PetService.class, petService, plugin, ServicePriority.High);
        Bukkit.getServicesManager().register(PetStatProvider.class, petService, plugin, ServicePriority.High);

        Bukkit.getPluginManager().registerEvents(new PetConnectionListener(petService), plugin);
        Bukkit.getPluginManager().registerEvents(new PetsGuiListener(petService), plugin);

        PetsCommand command = new PetsCommand(petService);
        DynamicCommandRegistrar.register(
                "skyrealms",
                "pets",
                "Open the Cubees Pets menu or manage your active pet",
                "/pets [rename|dismiss|give|help]",
                List.of("pet", "cubees", "cubee"),
                "skyrealms.pets.base",
                PermissionDefault.TRUE,
                "§cYou do not have permission to use /pets.",
                command
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            petService.handlePlayerJoin(online);
        }

        state = ModuleState.ENABLED;
        context.logger().info("SkyRealms Pets module enabled ({} Cubees companions active via ModelEngine).",
                catalog.all().size());
    }

    @Override
    public void onDisable(@NotNull ModuleContext context) throws Exception {
        context.logger().info("Disabling SkyRealms Pets module...");
        Bukkit.getServicesManager().unregister(PetStatProvider.class, plugin);
        Bukkit.getServicesManager().unregister(PetService.class, plugin);
        if (petService != null) {
            petService.close();
            petService = null;
        }
        state = ModuleState.DISABLED;
    }
}

