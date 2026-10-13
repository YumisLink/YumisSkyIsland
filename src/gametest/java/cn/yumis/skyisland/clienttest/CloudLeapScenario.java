package cn.yumis.skyisland.clienttest;

import cn.yumis.skyisland.effect.ModEffects;
import cn.yumis.skyisland.server.CloudLeapService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Exercises actual keyboard input, motion packets, damage Mixins and effect ticking. */
@SuppressWarnings("UnstableApiUsage")
public final class CloudLeapScenario implements FabricClientGameTest {
    private record CooldownProbe(MinecraftServer server, ServerPlayer player, int start,
                                 CompletableFuture<Void> result) {}
    private static CooldownProbe probe;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void resetPosition(ServerPlayer player, Vec3 start, float yaw) {
        player.teleportTo(player.level(), start.x, start.y, start.z, Set.of(), yaw, 80, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(false);
        player.hurtMarked = true;
        player.resetFallDistance();
    }

    private static void tickCooldownProbe(MinecraftServer server) {
        var active = probe;
        if (active == null || active.server() != server) return;
        int elapsed = server.getTickCount() - active.start();
        if (elapsed <= 0) return;
        try {
            check(elapsed <= CloudLeapService.COOLDOWN_TICKS, "Cooldown probe must run every server tick");
            var player = active.player();
            int duration = player.getEffect(ModEffects.CLOUD_STEP).getDuration();
            Vec3 before = player.getDeltaMovement();
            boolean accepted = CloudLeapService.leap(player, true);
            check(accepted == (elapsed == 5), "Leap must reject ticks 1-4 and accept tick 5");
            if (!accepted) {
                check(player.getEffect(ModEffects.CLOUD_STEP).getDuration() == duration,
                        "Rejected requests must not refresh protection");
                check(player.getDeltaMovement().equals(before), "Rejected requests must not change velocity");
            } else {
                check(player.getDeltaMovement().equals(new Vec3(0, 1.1, 0)), "Space mode must launch straight up");
                check(player.getEffect(ModEffects.CLOUD_STEP).getDuration() == 200,
                        "Successful airborne recast must refresh protection to 200 ticks");
                active.result().complete(null);
                probe = null;
            }
        } catch (Throwable error) {
            active.result().completeExceptionally(error);
            probe = null;
        }
    }

    @Override public void runTest(ClientGameTestContext context) {
        ServerTickEvents.START_SERVER_TICK.register(CloudLeapScenario::tickCooldownProbe);
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(mc -> {
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
            mc.resizeGui();
            mc.options.languageCode = "zh_cn";
            mc.getLanguageManager().setSelected("zh_cn");
        });
        var resources = context.computeOnClient(mc -> mc.reloadResourcePacks());
        context.waitFor(mc -> resources.isDone(), 800);
        resources.join();
        try (var world = context.worldBuilder().adjustSettings(settings -> {
            var flat = settings.getNormalPresetList().stream()
                    .filter(p -> p.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow();
            settings.setWorldType(flat);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
            settings.setAllowCommands(false);
        }).create()) {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksDownload();
            connection.waitForChunksRender();
            context.waitTicks(70); // Let vanilla's initial spawn invulnerability expire.
            Vec3 start = server.computeOnServer(s -> {
                var player = connection.getServerPlayer();
                BlockPos floor = player.blockPosition().below();
                for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++)
                    s.overworld().setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                return new Vec3(floor.getX() + .5, floor.getY() + 1, floor.getZ() + .5);
            });
            server.runOnServer(s -> resetPosition(connection.getServerPlayer(), start, 180));
            context.waitTicks(8);
            context.getInput().lookAt(180, 80);
            context.getInput().holdKey(GLFW.GLFW_KEY_C);
            server.waitFor(s -> connection.getServerPlayer().hasEffect(ModEffects.CLOUD_STEP), 40);
            context.waitTicks(12);
            context.getInput().releaseKey(GLFW.GLFW_KEY_C);
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                check(player.getZ() < start.z - 1, "C must move forward along yaw even while looking down");
                check(player.getY() > start.y + 1, "C must move upward on the actual client");
                check(player.getEffect(ModEffects.CLOUD_STEP).getDuration() < 195,
                        "Holding C must not auto-repeat the leap");
            });

            // Actual C + the native jump binding must choose the stronger vertical mode.
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                resetPosition(player, start, 180);
                player.removeEffect(ModEffects.CLOUD_STEP);
            });
            context.waitTicks(8);
            context.getInput().holdKey(options -> options.keyJump);
            context.getInput().pressKey(GLFW.GLFW_KEY_C);
            server.waitFor(s -> connection.getServerPlayer().hasEffect(ModEffects.CLOUD_STEP), 40);
            context.waitTicks(7);
            context.getInput().releaseKey(options -> options.keyJump);
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                check(player.getY() > start.y + 3, "C + Space must produce the higher jump");
                check(Math.abs(player.getX() - start.x) < .15 && Math.abs(player.getZ() - start.z) < .15,
                        "C + Space must launch upward without the normal forward impulse");
            });

            var cooldown = new CompletableFuture<Void>();
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                player.setYRot(90);
                player.setXRot(-90);
                player.setDeltaMovement(new Vec3(0, -2, 0));
                player.fallDistance = 20;
                check(CloudLeapService.leap(player, false), "An airborne falling player can recast");
                Vec3 velocity = player.getDeltaMovement();
                check(Math.abs(velocity.x + .8) < 1e-9 && Math.abs(velocity.z) < 1e-9 && velocity.y == .7,
                        "Forward mode must use server yaw and fixed upward speed regardless of pitch");
                check(player.fallDistance == 0, "A successful leap clears earlier fall distance");
                check(!CloudLeapService.leap(player, true), "A duplicate packet in the same tick is rejected");
                probe = new CooldownProbe(s, player, s.getTickCount(), cooldown);
            });
            server.waitFor(s -> cooldown.isDone(), 40);
            cooldown.join();

            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                player.setHealth(20);
                player.invulnerableTime = 0;
                check(!player.hurtServer(s.overworld(), player.damageSources().fall(), 8),
                        "Cloud Step must cancel native fall damage");
                check(player.getHealth() == 20, "Fall damage must not reduce health during protection");
                check(player.hurtServer(s.overworld(), player.damageSources().generic(), 2),
                        "Protection must not cancel unrelated damage");
                check(player.getHealth() == 18, "Other damage still reduces health");
                player.setHealth(20);
                player.invulnerableTime = 0;
                resetPosition(player, start.add(0, 20, 0), 180);
            });
            server.waitFor(s -> {
                var player = connection.getServerPlayer();
                return player.onGround() && Math.abs(player.getY() - start.y) < .1;
            }, 100);
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                check(Math.abs(player.getY() - start.y) < .1, "The high-fall test must reach the platform");
                check(player.getHealth() == 20, "An actual high fall must land without damage while protected");
            });
            context.waitFor(mc -> mc.player.hasEffect(ModEffects.CLOUD_STEP), 40);
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(3);
            context.takeScreenshot("cloud-leap-buff");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            server.waitFor(s -> !connection.getServerPlayer().hasEffect(ModEffects.CLOUD_STEP), 240);
            server.runOnServer(s -> {
                var player = connection.getServerPlayer();
                player.invulnerableTime = 0;
                check(player.hurtServer(s.overworld(), player.damageSources().fall(), 4),
                        "Fall damage must resume when the effect naturally expires");
                check(player.getHealth() == 16, "Expired protection must no longer prevent fall damage");
            });

            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.getInput().pressKey(GLFW.GLFW_KEY_C);
            context.waitTicks(8);
            server.runOnServer(s -> check(!connection.getServerPlayer().hasEffect(ModEffects.CLOUD_STEP),
                    "C must not trigger a leap while an inventory screen is open"));
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(8);
            server.runOnServer(s -> check(!connection.getServerPlayer().hasEffect(ModEffects.CLOUD_STEP),
                    "An ignored screen keypress must not trigger after the screen closes"));
        } finally {
            probe = null;
        }
    }
}
