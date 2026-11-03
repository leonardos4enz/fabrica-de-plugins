package dev.leo.worldcraft.paper;

import dev.leo.worldcraft.edit.PointsSource;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * El único código de Worldcraft que mira dentro del servidor (spec 040, ADR 0009): los *features* del juego (//feature)
 * y la forma de un bloque según sus vecinos (//update), que Paper no da por su API. Todo por reflexión con los nombres
 * de Mojang (Paper 1.21.4+ corre con ellos; el jar lo declara en su manifiesto). Si algo no está (otra versión), queda
 * apagado con el motivo y lo demás del plugin sigue igual.
 *
 * <p>Nunca escribe en el mundo: el juego trabaja contra un {@code WorldGenLevel} de mentira (un Proxy) que lee del mundo
 * y guarda lo que quería poner; eso lo pone el motor (vista previa, límites, deshacer). Hilo principal.
 */
public final class Nms {
    /** Lo que el juego intentó hacer directo en un chunk: el *feature* no se puede capturar. */
    public static final class Direct extends RuntimeException {
        Direct(String what) {
            super(what, null, false, false);
        }
    }

    private static Nms instance;

    public static synchronized Nms get() {
        if (instance == null) instance = new Nms();
        return instance;
    }

    private String problem;
    private Class<?> worldGenLevel, blockPos, blockState, levelAccessor;
    private Method getHandle, getChunkSource, getGenerator, registryGet, registryKeys, place, randomCreate, fromData, toData,
            updateShapes, getFluidState, isAir, rlParse, posX, posY, posZ;
    private Constructor<?> newPos;
    private Object registry, airState;

    private Nms() {
        try {
            ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld", false, cl);
            getHandle = craftWorld.getMethod("getHandle");
            Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel", false, cl);
            worldGenLevel = Class.forName("net.minecraft.world.level.WorldGenLevel", false, cl);
            levelAccessor = Class.forName("net.minecraft.world.level.LevelAccessor", false, cl);
            blockPos = Class.forName("net.minecraft.core.BlockPos", false, cl);
            blockState = Class.forName("net.minecraft.world.level.block.state.BlockState", false, cl);
            Class<?> vec3i = Class.forName("net.minecraft.core.Vec3i", false, cl);
            newPos = blockPos.getConstructor(int.class, int.class, int.class);
            posX = vec3i.getMethod("getX");
            posY = vec3i.getMethod("getY");
            posZ = vec3i.getMethod("getZ");
            getChunkSource = serverLevel.getMethod("getChunkSource");
            getGenerator = getChunkSource.getReturnType().getMethod("getGenerator");
            Class<?> rl = Class.forName("net.minecraft.resources.ResourceLocation", false, cl);
            rlParse = rl.getMethod("parse", String.class);
            Class<?> registries = Class.forName("net.minecraft.core.registries.Registries", false, cl);
            Object key = registries.getField("CONFIGURED_FEATURE").get(null);
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            Object access = server.getClass().getMethod("registryAccess").invoke(server);
            registry = one(access.getClass(), "lookupOrThrow", 1).invoke(access, key);
            registryGet = one(registry.getClass(), "getValue", 1, rl);
            registryKeys = registry.getClass().getMethod("keySet");
            Class<?> configured = Class.forName("net.minecraft.world.level.levelgen.feature.ConfiguredFeature", false, cl);
            Class<?> random = Class.forName("net.minecraft.util.RandomSource", false, cl);
            place = configured.getMethod("place", worldGenLevel, getGenerator.getReturnType(), random, blockPos);
            randomCreate = random.getMethod("create", long.class);
            Class<?> cbd = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData", false, cl);
            fromData = cbd.getMethod("fromData", blockState);
            toData = cbd.getMethod("getState");
            Class<?> block = Class.forName("net.minecraft.world.level.block.Block", false, cl);
            updateShapes = block.getMethod("updateFromNeighbourShapes", blockState, levelAccessor, blockPos);
            getFluidState = blockState.getMethod("getFluidState");
            isAir = blockState.getMethod("isAir");
            airState = toData.invoke(Bukkit.createBlockData(org.bukkit.Material.AIR));
        } catch (Throwable t) {
            problem = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /** El método público con ese nombre y cantidad de parámetros (y el primer parámetro de ese tipo, si se da). */
    private static Method one(Class<?> c, String name, int params, Class<?>... first) throws NoSuchMethodException {
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != params) continue;
            if (first.length > 0 && !m.getParameterTypes()[0].isAssignableFrom(first[0])) continue;
            return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }

    public boolean available() {
        return problem == null;
    }

    /** Por qué no está (null si está). */
    public String problem() {
        return problem;
    }

    // ------------------------------------------------------------------ features

    /** Los *features* del juego y de los paquetes de datos («minecraft:mossy_rock»…), en orden. */
    public List<String> features() {
        if (!available()) return List.of();
        try {
            TreeSet<String> out = new TreeSet<>();
            for (Object k : (Collection<?>) registryKeys.invoke(registry)) out.add(k.toString());
            return new ArrayList<>(out);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    /** ¿Existe ese *feature*? («mossy_rock» o «minecraft:mossy_rock») */
    public boolean hasFeature(String id) {
        return feature(id) != null;
    }

    private Object feature(String id) {
        try {
            Object rl = rlParse.invoke(null, id.contains(":") ? id : "minecraft:" + id);
            Object v = registryGet.invoke(registry, rl);
            return v instanceof Optional<?> o ? o.orElse(null) : v;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Un *feature* en (x, y, z) sin tocar el mundo: posición empaquetada → estado de Bukkit, o null si no se puso (no cabe,
     * el suelo no sirve). Lanza {@link Direct} si el juego quiso escribir directo en un chunk.
     */
    public Map<Long, BlockData> capture(World world, String id, int x, int y, int z, long seed) {
        Object f = feature(id);
        if (f == null) return null;
        try {
            Object level = getHandle.invoke(world);
            Object generator = getGenerator.invoke(getChunkSource.invoke(level));
            Capture cap = new Capture(level);
            Object proxy = Proxy.newProxyInstance(worldGenLevel.getClassLoader(), new Class<?>[]{worldGenLevel}, cap);
            Object ok = place.invoke(f, proxy, generator, randomCreate.invoke(null, seed), newPos.newInstance(x, y, z));
            if (!Boolean.TRUE.equals(ok) || cap.placed.isEmpty()) return null;
            Map<Long, BlockData> out = new LinkedHashMap<>();
            for (var e : cap.placed.entrySet()) out.put(e.getKey(), (BlockData) fromData.invoke(null, e.getValue()));
            return out;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Direct d) throw d;
            Throwable c = e.getCause();
            while (c instanceof InvocationTargetException || c instanceof java.lang.reflect.UndeclaredThrowableException) {
                c = c.getCause();
            }
            if (c instanceof Direct d) throw d;
            throw new IllegalStateException(c == null ? e : c);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ update

    /** La forma que tendría ese bloque con los vecinos que tiene (vallas, muros, paneles, escaleras); el mismo si no cambia. */
    public BlockData shape(World world, int x, int y, int z, BlockData current) {
        try {
            Object level = getHandle.invoke(world);
            Object proxy = Proxy.newProxyInstance(worldGenLevel.getClassLoader(), new Class<?>[]{worldGenLevel}, new Capture(level));
            Object st = toData.invoke(current);
            Object out = updateShapes.invoke(null, st, proxy, newPos.newInstance(x, y, z));
            return out == st ? current : (BlockData) fromData.invoke(null, out);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
        }
    }

    // ------------------------------------------------------------------ el mundo de mentira

    /**
     * Lee del mundo de verdad y guarda lo que el juego quiere poner. Lo que cambiaría el mundo por otro camino no se hace
     * (entidades, ticks, sonidos) o corta todo (el chunk directo).
     */
    private final class Capture implements InvocationHandler {
        final Object level;
        final Map<Long, Object> placed = new LinkedHashMap<>();
        final Map<String, Method> forward = new HashMap<>();

        Capture(Object level) {
            this.level = level;
        }

        long key(Object pos) throws ReflectiveOperationException {
            return PointsSource.pack((int) posX.invoke(pos), (int) posY.invoke(pos), (int) posZ.invoke(pos));
        }

        Object state(Object proxy, Object pos) throws Throwable {
            Object s = placed.get(key(pos));
            return s != null ? s : call(level, "getBlockState", new Class<?>[]{blockPos}, pos);
        }

        Object call(Object target, String name, Class<?>[] types, Object... args) throws Throwable {
            try {
                return target.getClass().getMethod(name, types).invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            String n = m.getName();
            int argc = args == null ? 0 : args.length;
            switch (n) {
                case "setBlock" -> {
                    placed.put(key(args[0]), args[1]);
                    return true;
                }
                case "removeBlock", "destroyBlock" -> {
                    placed.put(key(args[0]), airState);
                    return true;
                }
                case "getBlockState" -> {
                    if (argc == 1) return state(proxy, args[0]);
                }
                case "getFluidState" -> {
                    if (argc == 1) return getFluidState.invoke(state(proxy, args[0]));
                }
                case "isStateAtPosition" -> {
                    @SuppressWarnings("unchecked") Predicate<Object> p = (Predicate<Object>) args[1];
                    return p.test(state(proxy, args[0]));
                }
                case "isFluidAtPosition" -> {
                    @SuppressWarnings("unchecked") Predicate<Object> p = (Predicate<Object>) args[1];
                    return p.test(getFluidState.invoke(state(proxy, args[0])));
                }
                case "isEmptyBlock" -> {
                    if (argc == 1) return isAir.invoke(state(proxy, args[0]));
                }
                case "getBlockEntity" -> {
                    // El del mundo no se toca (el juego lo llenaría: un cofre, un generador).
                    return m.getReturnType() == Optional.class ? Optional.empty() : null;
                }
                case "addFreshEntity" -> {
                    return false;
                }
                case "getChunk", "getChunkForCollisions" -> throw new Direct(n);
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "Worldcraft capture";
                }
                default -> {
                }
            }
            if (m.getReturnType() == void.class) return null; // ticks, sonidos, partículas, eventos: nada
            if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
            try {
                return m.invoke(level, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
