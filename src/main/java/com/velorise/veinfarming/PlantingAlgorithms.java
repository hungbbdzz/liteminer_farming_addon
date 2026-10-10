package com.velorise.veinfarming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Geometric, spacing, and layout algorithms for smart planting:
 * - Smart Sapling grove alignment (2x2 mega clusters & spaced 1x1)
 * - Anti-overcrowding flora checkerboard (flowers, mushrooms, chorus flowers)
 * - Maximum Independent Set (MIS) bipartite matching for cacti
 * - Inverted Checkerboard 40/40 layout for melon/pumpkin fruit stems
 * - Intercropping row alternation with distance-weighted neighbor phase alignment
 */
public class PlantingAlgorithms {

    private static final int[] DY_ORDER = {0, 1, -1, 2, 3, 4, 5};

    /**
     * Checks if plantPos is within minSpacing of any already existing sapling, tree trunk (logs),
     * or foliage (leaves) in the world. Prevents overcrowding and repeat-planting saturation.
     */
    public static boolean isNearExistingTreeOrSapling(Level level, BlockPos plantPos, int minSpacing) {
        int checkRadius = Math.max(1, minSpacing - 1);
        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int px = plantPos.getX();
        int py = plantPos.getY();
        int pz = plantPos.getZ();

        for (int dy : DY_ORDER) {
            for (int dx = -checkRadius; dx <= checkRadius; dx++) {
                for (int dz = -checkRadius; dz <= checkRadius; dz++) {
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue;
                    }
                    mPos.set(px + dx, py + dy, pz + dz);
                    BlockState state = level.getBlockState(mPos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BlockTags.SAPLINGS) || state.is(PlantClassifier.C_BLOCK_SAPLINGS) || state.is(PlantClassifier.FORGE_BLOCK_SAPLINGS)
                            || state.getBlock() instanceof SaplingBlock) {
                        return true;
                    }
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Deterministic, uniform spatial 32-bit hash based on SplitMix64.
     */
    public static int hashPos(int x, int z, long seed) {
        long h = (long) x * 3129871L ^ (long) z * 116129781L ^ seed;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h = h ^ (h >>> 31);
        return (int) (h ^ (h >>> 32));
    }

    private static boolean containsOrigin(BlockPos clusterOrigin, BlockPos originSoilPos) {
        if (originSoilPos == null) {
            return false;
        }
        int dx = originSoilPos.getX() - clusterOrigin.getX();
        int dy = originSoilPos.getY() - clusterOrigin.getY();
        int dz = originSoilPos.getZ() - clusterOrigin.getZ();
        return dy == 0 && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
    }

    private static void add1x1SaplingsWithSpacing(Level level, List<BlockPos> soils, BlockPos originSoilPos,
                                                  int minSpacing, long seed, Set<BlockPos> chosenSaplingPositions,
                                                  List<BlockPos> result, Map<BlockPos, Boolean> nearCache) {
        List<BlockPos> candidates = new ArrayList<>(soils);
        candidates.sort(Comparator.comparingInt(p -> hashPos(p.getX(), p.getZ(), seed)));

        for (BlockPos soil : candidates) {
            BlockPos above = soil.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                continue;
            }
            boolean near = nearCache.computeIfAbsent(above, p -> isNearExistingTreeOrSapling(level, p, minSpacing));
            if (near) {
                continue;
            }

            boolean spacingOk = true;
            for (BlockPos chosen : chosenSaplingPositions) {
                if (Math.max(Math.abs(above.getX() - chosen.getX()), Math.abs(above.getZ() - chosen.getZ())) < minSpacing) {
                    spacingOk = false;
                    break;
                }
            }

            if (spacingOk) {
                result.add(soil);
                chosenSaplingPositions.add(above);
            }
        }
    }

    /**
     * Filters candidate soil positions for smart sapling planting.
     * Enforces minSpacing between trees, anti-overcrowding against existing trees/saplings,
     * and 2x2 cluster alignment for Dark Oak, Spruce, and Jungle.
     */
    public static List<BlockPos> filterSmartSaplingPositions(Level level, List<BlockPos> candidateSoilList,
                                                              ItemStack seedStack, BlockPos originSoilPos) {
        if (!FarmingConfig.isSmartSaplingEnabled() || !PlantClassifier.isSapling(seedStack) || candidateSoilList.isEmpty()) {
            return candidateSoilList;
        }

        Item seedItem = seedStack.getItem();
        if (!(seedItem instanceof BlockItem blockItem)) {
            return candidateSoilList;
        }
        Block saplingBlock = blockItem.getBlock();

        int minSpacing = FarmingConfig.SAPLING_MIN_SPACING.get();
        boolean enable2x2 = FarmingConfig.SMART_SAPLING_2X2.get();
        boolean isStrict2x2 = PlantClassifier.isStrictly2x2Sapling(saplingBlock);
        boolean isSupported2x2 = PlantClassifier.isSupported2x2Sapling(saplingBlock);

        long seed = (long) level.dimension().location().hashCode();

        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> chosenSaplingPositions = new HashSet<>();
        Set<BlockPos> candidateSoilSet = new HashSet<>(candidateSoilList);
        Map<BlockPos, Boolean> nearCache = new HashMap<>();

        if ((isStrict2x2 || isSupported2x2) && enable2x2) {
            // Find 2x2 clusters at same Y level
            List<BlockPos> potentialClusterOrigins = new ArrayList<>();
            for (BlockPos soil : candidateSoilList) {
                BlockPos p0 = soil;
                BlockPos p1 = soil.offset(1, 0, 0);
                BlockPos p2 = soil.offset(0, 0, 1);
                BlockPos p3 = soil.offset(1, 0, 1);

                if (candidateSoilSet.contains(p1) && candidateSoilSet.contains(p2) && candidateSoilSet.contains(p3)) {
                    boolean valid = true;
                    for (BlockPos p : List.of(p0, p1, p2, p3)) {
                        BlockPos above = p.above();
                        BlockState aboveState = level.getBlockState(above);
                        if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                            valid = false;
                            break;
                        }
                        boolean near = nearCache.computeIfAbsent(above, pos -> isNearExistingTreeOrSapling(level, pos, minSpacing));
                        if (near) {
                            valid = false;
                            break;
                        }
                    }
                    if (valid) {
                        potentialClusterOrigins.add(soil);
                    }
                }
            }

            final long finalSeed = seed;
            potentialClusterOrigins.sort((a, b) -> {
                boolean aContainsOrigin = containsOrigin(a, originSoilPos);
                boolean bContainsOrigin = containsOrigin(b, originSoilPos);
                if (aContainsOrigin && !bContainsOrigin) return -1;
                if (!aContainsOrigin && bContainsOrigin) return 1;
                return Integer.compare(hashPos(a.getX(), a.getZ(), finalSeed), hashPos(b.getX(), b.getZ(), finalSeed));
            });

            Set<BlockPos> usedSoilPositions = new HashSet<>();
            for (BlockPos origin : potentialClusterOrigins) {
                BlockPos p0 = origin;
                BlockPos p1 = origin.offset(1, 0, 0);
                BlockPos p2 = origin.offset(0, 0, 1);
                BlockPos p3 = origin.offset(1, 0, 1);
                List<BlockPos> clusterSoils = List.of(p0, p1, p2, p3);

                if (clusterSoils.stream().anyMatch(usedSoilPositions::contains)) {
                    continue;
                }

                boolean spacingOk = true;
                for (BlockPos p : clusterSoils) {
                    BlockPos above = p.above();
                    for (BlockPos chosen : chosenSaplingPositions) {
                        if (Math.max(Math.abs(above.getX() - chosen.getX()), Math.abs(above.getZ() - chosen.getZ())) < minSpacing) {
                            spacingOk = false;
                            break;
                        }
                    }
                    if (!spacingOk) break;
                }

                if (spacingOk) {
                    usedSoilPositions.addAll(clusterSoils);
                    result.addAll(clusterSoils);
                    for (BlockPos p : clusterSoils) {
                        chosenSaplingPositions.add(p.above());
                    }
                }
            }

            // Strictly 2x2 saplings (Dark Oak) cannot grow on 1x1, so only 2x2 clusters are planted
            if (isStrict2x2) {
                if (result.size() < 4) {
                    return Collections.emptyList();
                }
                return result;
            }

            // Supported 2x2 (Spruce, Jungle): leftover candidate soils can be planted as spaced 1x1 saplings
            List<BlockPos> remainingSoils = new ArrayList<>();
            for (BlockPos soil : candidateSoilList) {
                if (!usedSoilPositions.contains(soil)) {
                    remainingSoils.add(soil);
                }
            }
            add1x1SaplingsWithSpacing(level, remainingSoils, originSoilPos, minSpacing, finalSeed, chosenSaplingPositions, result, nearCache);
            if (result.size() < 2) {
                return Collections.emptyList();
            }
            return result;
        }

        // Standard 1x1 saplings (Oak, Birch, Acacia, Cherry, Mangrove, etc.)
        add1x1SaplingsWithSpacing(level, candidateSoilList, originSoilPos, minSpacing, seed, chosenSaplingPositions, result, nearCache);
        if (result.size() < 2) {
            return Collections.emptyList();
        }
        return result;
    }

    public static float hash2DFloat(int x, int z, long seed) {
        long h = ((long) x * 3129871L ^ (long) z * 116129781L ^ seed);
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h = h ^ (h >>> 31);
        return (float) ((h & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL);
    }

    public static float floraSmoothNoise(int x, int z, float scale, long seed) {
        float gx = x / scale;
        float gz = z / scale;
        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        float fx = gx - x0;
        float fz = gz - z0;
        float sx = fx * fx * (3.0f - 2.0f * fx);
        float sz = fz * fz * (3.0f - 2.0f * fz);

        float n00 = hash2DFloat(x0, z0, seed);
        float n10 = hash2DFloat(x1, z0, seed);
        float n01 = hash2DFloat(x0, z1, seed);
        float n11 = hash2DFloat(x1, z1, seed);

        float nx0 = n00 * (1.0f - sx) + n10 * sx;
        float nx1 = n01 * (1.0f - sx) + n11 * sx;
        return nx0 * (1.0f - sz) + nx1 * sz;
    }

    /**
     * Organic Flora & Mushroom Meadow Distribution:
     * Generates natural, irregular wildflower and mushroom distributions:
     * - Organic clumps ("crowd a bit"): 2-3 flowers grow close together in natural clusters.
     * - Sparse scattering ("sparse away"): Single blooms dotting the landscape with 2-4 block spaces.
     * - Natural clearings & irregular glades ("dont have a fixed shape"): Open breathing gaps without flowers,
     *   completely eliminating rigid 1-by-1 checkerboard grid monotony.
     * - Anti-overcrowding: Prevents dense solid slabs (no 2x2 blobs or 3-way orthogonal adjacency).
     * - Origin click preservation: The clicked block is always guaranteed to receive a plant if viable.
     * - Deterministic: Consistent preview and execution based on coordinates and world seed.
     */
    public static List<BlockPos> filterAntiOvercrowdedFloraPositions(
            Level level,
            List<BlockPos> candidateSoils,
            BlockPos originSoilPos,
            ItemStack seedStack
    ) {
        if (candidateSoils == null || candidateSoils.isEmpty()) {
            return Collections.emptyList();
        }
        if (!FarmingConfig.isSmartFlowerEnabled()) {
            return candidateSoils;
        }

        boolean isDoubleTall = seedStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof DoublePlantBlock;
        long seed = (long) level.dimension().location().hashCode() ^ 0x5a1f89c4L;

        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> chosenPlants = new HashSet<>();
        Set<BlockPos> candidateSoilSet = new HashSet<>(candidateSoils);

        // 1. Always prioritize the directly clicked origin soil pos
        if (originSoilPos != null && candidateSoilSet.contains(originSoilPos)) {
            BlockPos plantPos = originSoilPos.above();
            BlockState plantState = level.getBlockState(plantPos);
            boolean spaceOk = plantState.isAir() || plantState.canBeReplaced();
            if (isDoubleTall && spaceOk) {
                BlockState above2 = level.getBlockState(plantPos.above());
                if (!above2.isAir() && !above2.canBeReplaced()) {
                    spaceOk = false;
                }
            }
            if (spaceOk) {
                result.add(originSoilPos);
                chosenPlants.add(plantPos);
            }
        }

        // 2. Sort candidate soils outward from origin for natural spatial expansion
        List<BlockPos> sortedCandidates = new ArrayList<>(candidateSoils);
        if (originSoilPos != null) {
            sortedCandidates.sort(Comparator.comparingInt(p -> p.distManhattan(originSoilPos)));
        }

        // 3. Natural Organic Distribution
        for (BlockPos soil : sortedCandidates) {
            if (originSoilPos != null && soil.equals(originSoilPos)) {
                continue;
            }

            BlockPos plantPos = soil.above();
            BlockState plantState = level.getBlockState(plantPos);
            if (!plantState.isAir() && !plantState.canBeReplaced()) {
                continue;
            }

            if (isDoubleTall) {
                BlockState above2 = level.getBlockState(plantPos.above());
                if (!above2.isAir() && !above2.canBeReplaced()) {
                    continue;
                }
            }

            // Count pre-existing flowers/mushrooms in the world adjacent to plantPos
            int existingAdjacent = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockState neighborState = level.getBlockState(plantPos.relative(dir));
                if (PlantClassifier.isFlowerBlock(neighborState) || PlantClassifier.isMushroomBlock(neighborState)) {
                    existingAdjacent++;
                }
            }
            if (existingAdjacent >= 2) {
                // Too close to pre-existing clumps in the world
                continue;
            }

            // Count orthogonal chosen neighbors (distance = 1)
            int chosenAdjacent = 0;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (chosenPlants.contains(plantPos.relative(dir))) {
                    chosenAdjacent++;
                }
            }
            int totalAdjacent = existingAdjacent + chosenAdjacent;

            // Count diagonal chosen neighbors (distance = sqrt(2))
            int diagonalAdjacent = 0;
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    if (chosenPlants.contains(plantPos.offset(dx, 0, dz))) {
                        diagonalAdjacent++;
                    }
                }
            }

            int sparsity = FarmingConfig.FLOWER_SPARSITY.get();
            if (sparsity <= 0) {
                result.add(soil);
                chosenPlants.add(plantPos);
                continue;
            }

            // Coherent multi-scale organic meadow noise
            float cNoise = floraSmoothNoise(soil.getX(), soil.getZ(), 6.5f, seed);
            float dNoise = hash2DFloat(soil.getX(), soil.getZ(), seed + 1013L);

            boolean accept = false;

            // Higher sparsity = smaller clusters and wider spacing (Default 3 is noticeably more sparse)
            float coreThreshold = 0.50f + (sparsity * 0.04f); // sparsity 3: 0.62f
            float meadowThreshold = 0.25f + (sparsity * 0.02f); // sparsity 3: 0.31f

            float clusterProb = Math.max(0.10f, 0.65f - (sparsity * 0.08f)); // sparsity 3: 0.41f
            float sparseProb = Math.max(0.04f, 0.32f - (sparsity * 0.05f));  // sparsity 3: 0.17f
            float gladeProb = Math.max(0.01f, 0.08f - (sparsity * 0.02f));   // sparsity 3: 0.02f

            int maxClusterNeighbors = (sparsity >= 3) ? 1 : 2;

            if (cNoise >= coreThreshold) {
                // Zone A: Cluster Core ("crowd a bit")
                if (totalAdjacent <= 1 && (totalAdjacent + diagonalAdjacent) <= maxClusterNeighbors) {
                    if (dNoise < clusterProb) {
                        accept = true;
                    }
                }
            } else if (cNoise >= meadowThreshold) {
                // Zone B: Sparse Meadow ("sparse away")
                if (totalAdjacent == 0) {
                    if (dNoise < sparseProb) {
                        accept = true;
                    }
                }
            } else {
                // Zone C: Open Glade ("dont have a fixed shape")
                if (totalAdjacent == 0 && diagonalAdjacent == 0) {
                    if (dNoise < gladeProb) {
                        accept = true;
                    }
                }
            }

            if (accept) {
                result.add(soil);
                chosenPlants.add(plantPos);
            }
        }

        return result;
    }

    /**
     * Optimal Cactus Planting using Maximum Independent Set on Bipartite Grid Graph:
     * Guarantees no two cacti ever collide horizontally (orthogonally), respects pre-existing obstacles
     * and rogue scattered cacti, and mathematically maximizes the total count of planted cacti.
     */
    public static List<BlockPos> filterOptimalCactusPositions(
            Level level,
            List<BlockPos> candidateSoils,
            BlockPos originSoilPos
    ) {
        if (candidateSoils == null || candidateSoils.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Filter out candidate soils whose plant position is not viable
        // (must have air above, and NO existing solid block, liquid, or cactus in 4 horizontal directions)
        List<BlockPos> viableSoils = new ArrayList<>();
        for (BlockPos soil : candidateSoils) {
            BlockPos plantPos = soil.above();
            BlockState aboveState = level.getBlockState(plantPos);
            if (!aboveState.isAir() && !aboveState.canBeReplaced()) {
                continue;
            }

            boolean hasObstacle = false;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = plantPos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.isSolid() || neighborState.is(Blocks.CACTUS) || neighborState.liquid()) {
                    hasObstacle = true;
                    break;
                }
            }

            if (!hasObstacle) {
                viableSoils.add(soil);
            }
        }

        if (viableSoils.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Partition viable candidates into Bipartite sets L and R based on (x + z) % 2
        List<BlockPos> setL = new ArrayList<>();
        List<BlockPos> setR = new ArrayList<>();
        Map<BlockPos, Integer> indexInR = new HashMap<>();

        for (BlockPos pos : viableSoils) {
            int parity = Math.floorMod(pos.getX() + pos.getZ(), 2);
            if (parity == 0) {
                setL.add(pos);
            } else {
                indexInR.put(pos, setR.size());
                setR.add(pos);
            }
        }

        if (setL.isEmpty() || setR.isEmpty()) {
            return viableSoils;
        }

        // Build adjacency list for bipartite graph: edge exists if Manhattan distance == 1 horizontally
        int nL = setL.size();
        int nR = setR.size();
        List<List<Integer>> adj = new ArrayList<>(nL);
        for (int i = 0; i < nL; i++) {
            adj.add(new ArrayList<>());
        }

        for (int i = 0; i < nL; i++) {
            BlockPos pL = setL.get(i);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighbor = pL.relative(dir);
                Integer rIdx = indexInR.get(neighbor);
                if (rIdx != null) {
                    adj.get(i).add(rIdx);
                } else {
                    Integer rIdxUp = indexInR.get(neighbor.above());
                    if (rIdxUp != null) adj.get(i).add(rIdxUp);
                    Integer rIdxDown = indexInR.get(neighbor.below());
                    if (rIdxDown != null) adj.get(i).add(rIdxDown);
                }
            }
        }

        // 3. Maximum Bipartite Matching using augmenting paths (DFS)
        int[] matchL = new int[nL];
        Arrays.fill(matchL, -1);
        int[] matchR = new int[nR];
        Arrays.fill(matchR, -1);

        for (int i = 0; i < nL; i++) {
            boolean[] visited = new boolean[nR];
            dfsBipartiteMatch(i, adj, matchR, matchL, visited);
        }

        // 4. Konig's Theorem: find all reachable vertices from unmatched vertices in L
        boolean[] reachableL = new boolean[nL];
        boolean[] reachableR = new boolean[nR];
        Queue<Integer> queue = new ArrayDeque<>();

        for (int i = 0; i < nL; i++) {
            if (matchL[i] == -1) {
                reachableL[i] = true;
                queue.add(i);
            }
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v : adj.get(u)) {
                if (matchL[u] != v && !reachableR[v]) {
                    reachableR[v] = true;
                    int matchedU = matchR[v];
                    if (matchedU != -1 && !reachableL[matchedU]) {
                        reachableL[matchedU] = true;
                        queue.add(matchedU);
                    }
                }
            }
        }

        // 5. Maximum Independent Set (L reachable + R unreachable)
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < nL; i++) {
            if (reachableL[i]) {
                result.add(setL.get(i));
            }
        }
        for (int j = 0; j < nR; j++) {
            if (!reachableR[j]) {
                result.add(setR.get(j));
            }
        }

        result.sort(Comparator.comparingInt(p -> p.distManhattan(originSoilPos)));
        return result;
    }

    private static boolean dfsBipartiteMatch(
            int u,
            List<List<Integer>> adj,
            int[] matchR,
            int[] matchL,
            boolean[] visited
    ) {
        for (int v : adj.get(u)) {
            if (!visited[v]) {
                visited[v] = true;
                if (matchR[v] < 0 || dfsBipartiteMatch(matchR[v], adj, matchR, matchL, visited)) {
                    matchR[v] = u;
                    matchL[u] = v;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Optimal Melon & Pumpkin Stem Planting (Inverted Checkerboard 40/40 Layout):
     * Places stems exclusively in a checkerboard pattern aligned with water/existing stems,
     * guaranteeing every single stem has empty adjacent fruit spots and zero growth speed penalty!
     */
    public static List<BlockPos> filterOptimalFruitStemPositions(
            Level level,
            List<BlockPos> candidateSoils,
            BlockPos originSoilPos
    ) {
        if (candidateSoils == null || candidateSoils.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Determine target parity for stems:
        // Priority A: Align with existing stems in candidate soils
        int existingStemCount0 = 0;
        int existingStemCount1 = 0;
        for (BlockPos soil : candidateSoils) {
            BlockState above = level.getBlockState(soil.above());
            if (PlantClassifier.isStem(above)) {
                int p = Math.floorMod(soil.getX() + soil.getZ(), 2);
                if (p == 0) existingStemCount0++;
                else existingStemCount1++;
            }
        }

        int targetParity;
        if (existingStemCount0 > 0 || existingStemCount1 > 0) {
            targetParity = (existingStemCount0 >= existingStemCount1) ? 0 : 1;
        } else {
            // Priority B: Check for nearby water within radius 6 (e.g. 9x9 farm center water block)
            BlockPos waterPos = PlantClassifier.findNearbyWaterBlock(level, originSoilPos, 6);
            if (waterPos != null) {
                int waterParity = Math.floorMod(waterPos.getX() + waterPos.getZ(), 2);
                // Stems sit on the opposite parity of water so water block falls on a fruit spot!
                targetParity = 1 - waterParity;
            } else {
                // Priority C: Count viable candidates for parity 0 vs 1 and pick the larger set
                int count0 = 0;
                int count1 = 0;
                for (BlockPos soil : candidateSoils) {
                    if (Math.floorMod(soil.getX() + soil.getZ(), 2) == 0) count0++;
                    else count1++;
                }
                targetParity = (count0 >= count1) ? 0 : 1;
            }
        }

        // 2. Select candidates strictly matching targetParity
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos soil : candidateSoils) {
            if (Math.floorMod(soil.getX() + soil.getZ(), 2) == targetParity) {
                BlockPos above = soil.above();
                BlockState aboveState = level.getBlockState(above);
                if (aboveState.isAir() || aboveState.canBeReplaced()) {
                    result.add(soil);
                }
            }
        }

        result.sort(Comparator.comparingInt(p -> p.distManhattan(originSoilPos)));
        return result;
    }

    private static BlockState findCropAtColumn(Level level, int x, int baseY, int z) {
        BlockPos p = new BlockPos(x, baseY, z);
        BlockState s = level.getBlockState(p);
        if (!s.isAir()) return s;
        BlockState sUp = level.getBlockState(p.above());
        if (!sUp.isAir()) return sUp;
        BlockState sDown = level.getBlockState(p.below());
        if (!sDown.isAir()) return sDown;
        return s;
    }

    /**
     * Checks if placing candidateCrop at plantPos will suffer the vanilla 50% growth penalty
     * (when the same crop is present in both orthogonal axes or on any diagonal).
     */
    public static boolean hasGrowthPenalty(
            Level level,
            BlockPos plantPos,
            Block candidateCrop,
            Map<BlockPos, Block> plannedCrops
    ) {
        if (candidateCrop == null || level == null) {
            return false;
        }

        BlockPos north = plantPos.north();
        BlockPos south = plantPos.south();
        BlockPos east = plantPos.east();
        BlockPos west = plantPos.west();

        boolean sameX = isMatchingCropAt(level, west, candidateCrop, plannedCrops)
                || isMatchingCropAt(level, east, candidateCrop, plannedCrops);
        boolean sameZ = isMatchingCropAt(level, north, candidateCrop, plannedCrops)
                || isMatchingCropAt(level, south, candidateCrop, plannedCrops);

        if (sameX && sameZ) {
            return true;
        }

        boolean diagonal = isMatchingCropAt(level, west.north(), candidateCrop, plannedCrops)
                || isMatchingCropAt(level, east.north(), candidateCrop, plannedCrops)
                || isMatchingCropAt(level, east.south(), candidateCrop, plannedCrops)
                || isMatchingCropAt(level, west.south(), candidateCrop, plannedCrops);

        return diagonal;
    }

    private static boolean isMatchingCropAt(
            Level level,
            BlockPos cropPos,
            Block targetCrop,
            Map<BlockPos, Block> plannedCrops
    ) {
        if (plannedCrops != null && plannedCrops.containsKey(cropPos)) {
            Block planned = plannedCrops.get(cropPos);
            return PlantClassifier.isMatchingCrop(planned != null ? planned.defaultBlockState() : null, targetCrop);
        }

        BlockState existing = findCropAtColumn(level, cropPos.getX(), cropPos.getY(), cropPos.getZ());
        return PlantClassifier.isMatchingCrop(existing, targetCrop);
    }

    /**
     * Determines whether even rows (relative to origin) should be the main hand crop
     * using distance-weighted voting from existing crops in the vicinity.
     * Prevents any two adjacent parallel rows from ever having the same crop type.
     */
    public static boolean determineIntercropPhase(
            Level level,
            BlockPos originPos,
            Direction facing,
            Block mainCropBlock,
            Block offCropBlock
    ) {
        if (mainCropBlock == null || offCropBlock == null || level == null) {
            return true;
        }

        boolean alternateOnX = (facing.getAxis() == Direction.Axis.Z);
        int cropY = originPos.getY() + 1;
        float score = 0.0f;

        int originTransverse = alternateOnX ? originPos.getX() : originPos.getZ();

        // Scan surrounding farm (transverse radius 12, axial radius 8)
        for (int dt = -12; dt <= 12; dt++) {
            int currentTransverse = originTransverse + dt;
            int rowParity = Math.floorMod(dt, 2); // 0 = even, 1 = odd
            float weight = 12.0f / (Math.abs(dt) + 1.0f); // Closer to clicked row = much stronger weight

            for (int da = -8; da <= 8; da++) {
                BlockState s = alternateOnX
                        ? findCropAtColumn(level, currentTransverse, cropY, originPos.getZ() + da)
                        : findCropAtColumn(level, originPos.getX() + da, cropY, currentTransverse);

                if (PlantClassifier.isMatchingCrop(s, mainCropBlock)) {
                    score += (rowParity == 0) ? weight : -weight;
                } else if (PlantClassifier.isMatchingCrop(s, offCropBlock)) {
                    score += (rowParity == 0) ? -weight : weight;
                }
            }
        }

        if (score > 0.0f) {
            return true;
        } else if (score < 0.0f) {
            return false;
        }

        return true;
    }

    /**
     * Smart Context-Aware Intercropping with Strict Alternation:
     * Guarantees Row(i) != Row(i + 1) for all rows, completely preventing adjacent same-crop collisions
     * while aligning the entire field's phase with existing planted crops.
     */
    public static boolean isMainCropRow(
            Level level,
            BlockPos soilPos,
            BlockPos originPos,
            Direction facing,
            Block mainCropBlock,
            Block offCropBlock
    ) {
        boolean alternateOnX = (facing.getAxis() == Direction.Axis.Z);
        int rowCoord = alternateOnX ? (soilPos.getX() - originPos.getX()) : (soilPos.getZ() - originPos.getZ());
        boolean isEven = Math.floorMod(rowCoord, 2) == 0;

        boolean evenIsMain = determineIntercropPhase(level, originPos, facing, mainCropBlock, offCropBlock);
        return isEven ? evenIsMain : !evenIsMain;
    }

    public static boolean isMainCropRow(BlockPos pos, BlockPos originPos, Direction facing) {
        boolean alternateOnX = (facing.getAxis() == Direction.Axis.Z);
        int rowCoord = alternateOnX ? (pos.getX() - originPos.getX()) : (pos.getZ() - originPos.getZ());
        return Math.floorMod(rowCoord, 2) == 0;
    }

    /**
     * Checks if the given position is within vanilla hydration range (9x9 horizontal, dy in [0, 1])
     * of any of the planned water holes.
     */
    public static boolean isHydratedByHoles(BlockPos pos, Collection<BlockPos> waterHoles) {
        if (waterHoles == null || waterHoles.isEmpty()) {
            return false;
        }
        for (BlockPos hole : waterHoles) {
            int dx = Math.abs(pos.getX() - hole.getX());
            int dz = Math.abs(pos.getZ() - hole.getZ());
            int dy = hole.getY() - pos.getY();
            if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a candidate position is too close to any existing or planned water sources.
     * Enforces strict Chebyshev distance >= minSpacing (minimum 8 blocks).
     * If max(|dx|, |dz|) < minSpacing, their 9x9 hydration zones overlap, so it is strictly rejected.
     */
    public static boolean isTooCloseToWater(BlockPos cand, Collection<BlockPos> waterSources, int minSpacing) {
        if (cand == null || waterSources == null || waterSources.isEmpty()) {
            return false;
        }
        for (BlockPos w : waterSources) {
            int dx = Math.abs(cand.getX() - w.getX());
            int dz = Math.abs(cand.getZ() - w.getZ());
            if (Math.max(dx, dz) < minSpacing) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scans for existing water sources in the world within the vicinity of the bounding box.
     */
    public static List<BlockPos> findNearbyWorldWater(Level level, Collection<BlockPos> positions, int searchRadius) {
        if (level == null || positions == null || positions.isEmpty()) {
            return Collections.emptyList();
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : positions) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY());
            maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }

        int scanMinX = minX - searchRadius;
        int scanMaxX = maxX + searchRadius;
        int scanMinZ = minZ - searchRadius;
        int scanMaxZ = maxZ + searchRadius;
        int scanMinY = minY - 2;
        int scanMaxY = maxY + 2;

        List<BlockPos> waters = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = scanMinX; x <= scanMaxX; x++) {
            for (int z = scanMinZ; z <= scanMaxZ; z++) {
                cursor.set(x, minY, z);
                if (!level.hasChunkAt(cursor)) {
                    continue;
                }
                for (int y = scanMinY; y <= scanMaxY; y++) {
                    cursor.set(x, y, z);
                    if (level.getFluidState(cursor).is(FluidTags.WATER) || level.getBlockState(cursor).is(Blocks.WATER)) {
                        waters.add(cursor.immutable());
                    }
                }
            }
        }
        return waters;
    }

    /**
     * Calculates optimal water hole locations for tilling/irrigation when holding a Hoe and Water Bucket.
     *
     * 2-Layer Optimization:
     * Layer 1: Farm Grid Alignment & Coordinate Anchoring
     *   - Detects all existing water sources in the nearby world.
     *   - If an existing water source is nearby, anchors the grid directly to that existing water source,
     *     ensuring the new field continues the exact same orthogonal farm grid.
     *   - If no existing water source is found, centers the optimal grid over the tilled area
     *     to maximize full 9x9 coverage with minimum water holes.
     *
     * Layer 2: Strict Spacing & Zero Redundant Overlap
     *   - Strictly enforces max(|dx|, |dz|) >= minSpacing (minimum 8 blocks) against ALL water sources:
     *     both existing world water sources and newly selected water holes.
     *   - Rejects any candidate that would overlap with existing or planned water supply zones.
     *   - Preserves maximum farmland surface while guaranteeing full moisture coverage without dry gaps.
     */
    public static List<BlockPos> calculateOptimalWaterHoles(
            Level level,
            Collection<BlockPos> tilledPositions,
            BlockPos clickedPos,
            int maxWaterHoles
    ) {
        if (tilledPositions == null || tilledPositions.size() < 2 || maxWaterHoles <= 0 || level == null) {
            return Collections.emptyList();
        }

        int minSpacing = FarmingConfig.WATER_HOLE_SPACING.get();
        if (minSpacing < 8) {
            minSpacing = 8;
        }

        // 1. Scan for existing water sources in the world nearby (within 12 blocks)
        List<BlockPos> existingWorldWaters = findNearbyWorldWater(level, tilledPositions, 12);

        // 2. Identify which positions are NOT yet hydrated by existing world water
        Set<BlockPos> unhydrated = new HashSet<>();
        for (BlockPos pos : tilledPositions) {
            boolean hydrated = false;
            for (BlockPos w : existingWorldWaters) {
                int dx = Math.abs(pos.getX() - w.getX());
                int dz = Math.abs(pos.getZ() - w.getZ());
                int dy = w.getY() - pos.getY();
                if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                    hydrated = true;
                    break;
                }
            }
            if (!hydrated) {
                unhydrated.add(pos);
            }
        }

        if (unhydrated.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Find bounding box of tilled positions
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : tilledPositions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // 4. Determine Grid Origin (Layer 1)
        BlockPos anchorWater = null;
        double bestAnchorDistSq = Double.MAX_VALUE;
        for (BlockPos w : existingWorldWaters) {
            double d = w.distSqr(clickedPos);
            if (d < bestAnchorDistSq) {
                bestAnchorDistSq = d;
                anchorWater = w;
            }
        }

        int originX;
        int originZ;
        if (anchorWater != null) {
            originX = anchorWater.getX();
            originZ = anchorWater.getZ();
        } else {
            int width = maxX - minX + 1;
            int height = maxZ - minZ + 1;
            if (width <= 9) {
                originX = minX + width / 2;
            } else {
                int kx = Math.max(1, (int) Math.round((width - 9.0) / minSpacing) + 1);
                int marginX = Math.max(0, (width - ((kx - 1) * minSpacing + 1)) / 2);
                originX = minX + marginX;
            }

            if (height <= 9) {
                originZ = minZ + height / 2;
            } else {
                int kz = Math.max(1, (int) Math.round((height - 9.0) / minSpacing) + 1);
                int marginZ = Math.max(0, (height - ((kz - 1) * minSpacing + 1)) / 2);
                originZ = minZ + marginZ;
            }
        }

        // 5. Generate Ideal Grid Targets
        int minGridI = (int) Math.floor((minX - originX - 4.0) / minSpacing);
        int maxGridI = (int) Math.ceil((maxX - originX + 4.0) / minSpacing);
        int minGridJ = (int) Math.floor((minZ - originZ - 4.0) / minSpacing);
        int maxGridJ = (int) Math.ceil((maxZ - originZ + 4.0) / minSpacing);

        List<int[]> idealGridPoints = new ArrayList<>();
        for (int i = minGridI; i <= maxGridI; i++) {
            for (int j = minGridJ; j <= maxGridJ; j++) {
                idealGridPoints.add(new int[]{originX + i * minSpacing, originZ + j * minSpacing});
            }
        }
        idealGridPoints.sort(Comparator.comparingDouble(gp -> {
            double dx = gp[0] - clickedPos.getX();
            double dz = gp[1] - clickedPos.getZ();
            return dx * dx + dz * dz;
        }));

        // 6. Map ideal grid points to best available candidate positions in tilledPositions
        List<BlockPos> candidatePositions = new ArrayList<>();
        for (int[] gp : idealGridPoints) {
            int targetX = gp[0];
            int targetZ = gp[1];

            BlockPos bestCand = null;
            double bestDistSq = Double.MAX_VALUE;

            for (BlockPos pos : tilledPositions) {
                if (!level.getBlockState(pos.below()).isSolid()) {
                    continue;
                }
                if (isTooCloseToWater(pos, existingWorldWaters, minSpacing)) {
                    continue;
                }

                int dx = Math.abs(pos.getX() - targetX);
                int dz = Math.abs(pos.getZ() - targetZ);
                if (dx <= 2 && dz <= 2) {
                    double d = dx * dx + dz * dz;
                    if (d < bestDistSq) {
                        bestDistSq = d;
                        bestCand = pos;
                    }
                }
            }

            if (bestCand != null && !candidatePositions.contains(bestCand)) {
                candidatePositions.add(bestCand);
            }
        }

        // Fallback candidates for irregular shapes
        List<BlockPos> fallbackCandidates = new ArrayList<>();
        for (BlockPos pos : tilledPositions) {
            if (!candidatePositions.contains(pos) && level.getBlockState(pos.below()).isSolid()) {
                if (!isTooCloseToWater(pos, existingWorldWaters, minSpacing)) {
                    fallbackCandidates.add(pos);
                }
            }
        }
        fallbackCandidates.sort(Comparator.comparingInt(pos -> pos.distManhattan(clickedPos)));
        candidatePositions.addAll(fallbackCandidates);

        // 7. Greedy Selection - Pass 1: Strict Minimum Spacing Grid (Highest Priority)
        // Strictly enforces minSpacing (default 8 blocks) against all existing world water
        // and newly selected water holes. Preserves maximum farmland surface without redundant overlap.
        Set<BlockPos> covered = new HashSet<>();
        List<BlockPos> selectedHoles = new ArrayList<>();

        while (selectedHoles.size() < maxWaterHoles && covered.size() < unhydrated.size()) {
            BlockPos bestChoice = null;
            int maxNewlyCovered = 0;
            double bestDistToGrid = Double.MAX_VALUE;

            for (BlockPos cand : candidatePositions) {
                if (selectedHoles.contains(cand)) {
                    continue;
                }

                // STRICT RULE 1: Never closer than minSpacing to ANY existing water in the world
                if (isTooCloseToWater(cand, existingWorldWaters, minSpacing)) {
                    continue;
                }

                // STRICT RULE 2: Never closer than minSpacing to ANY newly selected water hole
                if (isTooCloseToWater(cand, selectedHoles, minSpacing)) {
                    continue;
                }

                // Count how many currently unhydrated blocks this candidate will cover
                int newlyCovered = 0;
                for (BlockPos u : unhydrated) {
                    if (!covered.contains(u)) {
                        int dx = Math.abs(u.getX() - cand.getX());
                        int dz = Math.abs(u.getZ() - cand.getZ());
                        int dy = cand.getY() - u.getY();
                        if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                            newlyCovered++;
                        }
                    }
                }

                if (newlyCovered > 0) {
                    int modX = Math.abs(Math.floorMod(cand.getX() - originX, minSpacing));
                    if (modX > minSpacing / 2) modX = minSpacing - modX;
                    int modZ = Math.abs(Math.floorMod(cand.getZ() - originZ, minSpacing));
                    if (modZ > minSpacing / 2) modZ = minSpacing - modZ;
                    double gridOffsetDist = modX * modX + modZ * modZ;

                    if (newlyCovered > maxNewlyCovered || (newlyCovered == maxNewlyCovered && gridOffsetDist < bestDistToGrid)) {
                        maxNewlyCovered = newlyCovered;
                        bestDistToGrid = gridOffsetDist;
                        bestChoice = cand;
                    }
                }
            }

            if (bestChoice == null || maxNewlyCovered == 0) {
                break;
            }

            selectedHoles.add(bestChoice);
            for (BlockPos u : unhydrated) {
                int dx = Math.abs(u.getX() - bestChoice.getX());
                int dz = Math.abs(u.getZ() - bestChoice.getZ());
                int dy = bestChoice.getY() - u.getY();
                if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                    covered.add(u);
                }
            }
        }

        // 8. Full Coverage Rescue - Pass 2: Guarantee All Farmland Irrigated
        // If Pass 1 left orphan/dead-zone farmland unhydrated (e.g. narrow peninsulas, irregular edges,
        // or pockets surrounded by existing water at distance 5-7), gracefully place rescue water holes
        // to ensure 100% of the farmland is hydrated without leaving dry dead dirt.
        int rescueMinSpacing = 4; // Start with minimum 4 blocks separation (at least 3 farmland blocks between water holes)
        while (selectedHoles.size() < maxWaterHoles && covered.size() < unhydrated.size()) {
            BlockPos bestRescue = null;
            int maxNewlyCovered = 0;
            int bestMinDistToWater = -1;
            double bestDistToCenter = Double.MAX_VALUE;

            for (BlockPos cand : tilledPositions) {
                if (selectedHoles.contains(cand)) {
                    continue;
                }
                if (!level.getBlockState(cand.below()).isSolid()) {
                    continue;
                }

                // Minimum sanity distance: never place water right next to another water hole
                if (isTooCloseToWater(cand, existingWorldWaters, rescueMinSpacing)
                        || isTooCloseToWater(cand, selectedHoles, rescueMinSpacing)) {
                    continue;
                }

                // Count unhydrated blocks this rescue candidate covers
                int newlyCovered = 0;
                for (BlockPos u : unhydrated) {
                    if (!covered.contains(u)) {
                        int dx = Math.abs(u.getX() - cand.getX());
                        int dz = Math.abs(u.getZ() - cand.getZ());
                        int dy = cand.getY() - u.getY();
                        if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                            newlyCovered++;
                        }
                    }
                }

                if (newlyCovered > 0) {
                    // Calculate Chebyshev distance to nearest water source to maximize spacing
                    int minDistToWater = Integer.MAX_VALUE;
                    for (BlockPos w : existingWorldWaters) {
                        minDistToWater = Math.min(minDistToWater, Math.max(Math.abs(cand.getX() - w.getX()), Math.abs(cand.getZ() - w.getZ())));
                    }
                    for (BlockPos w : selectedHoles) {
                        minDistToWater = Math.min(minDistToWater, Math.max(Math.abs(cand.getX() - w.getX()), Math.abs(cand.getZ() - w.getZ())));
                    }

                    double distToClicked = cand.distSqr(clickedPos);

                    // Ranking criteria:
                    // 1. Maximize newly covered unhydrated blocks
                    // 2. Maximize distance to closest water (centers the hole in the dead zone, furthest from existing water)
                    // 3. Minimize distance to clicked position
                    if (newlyCovered > maxNewlyCovered
                            || (newlyCovered == maxNewlyCovered && minDistToWater > bestMinDistToWater)
                            || (newlyCovered == maxNewlyCovered && minDistToWater == bestMinDistToWater && distToClicked < bestDistToCenter)) {
                        maxNewlyCovered = newlyCovered;
                        bestMinDistToWater = minDistToWater;
                        bestDistToCenter = distToClicked;
                        bestRescue = cand;
                    }
                }
            }

            if (bestRescue == null || maxNewlyCovered == 0) {
                // If rescueMinSpacing = 4 couldn't find a spot (e.g. an extremely narrow 1-wide pocket),
                // step down to 3 or 2 to guarantee irrigation
                if (rescueMinSpacing > 2) {
                    rescueMinSpacing--;
                    continue;
                }
                break;
            }

            selectedHoles.add(bestRescue);
            for (BlockPos u : unhydrated) {
                int dx = Math.abs(u.getX() - bestRescue.getX());
                int dz = Math.abs(u.getZ() - bestRescue.getZ());
                int dy = bestRescue.getY() - u.getY();
                if (dx <= 4 && dz <= 4 && dy >= 0 && dy <= 1) {
                    covered.add(u);
                }
            }
        }

        return selectedHoles;
    }
}

