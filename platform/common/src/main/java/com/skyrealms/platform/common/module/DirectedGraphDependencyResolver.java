package com.skyrealms.platform.common.module;

import com.skyrealms.platform.api.module.ModuleId;
import com.skyrealms.platform.api.module.SkyModule;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Directed acyclic graph (DAG) dependency resolver using topological sort.
 */
public final class DirectedGraphDependencyResolver implements DependencyResolver {

    @Override
    @NotNull
    public List<SkyModule> resolve(@NotNull Collection<SkyModule> modules) {
        Objects.requireNonNull(modules, "modules cannot be null");
        if (modules.isEmpty()) {
            return Collections.emptyList();
        }

        Map<ModuleId, SkyModule> byId = new HashMap<>();
        for (SkyModule module : modules) {
            byId.put(module.id(), module);
        }

        // 1. Verify required dependencies exist
        for (SkyModule module : modules) {
            for (ModuleId req : module.metadata().dependencies()) {
                if (!byId.containsKey(req)) {
                    throw new MissingDependencyException(module.id(), req);
                }
            }
        }

        // 2. Build adjacency list: dependency -> dependents (dep must enable BEFORE dependent)
        Map<ModuleId, Set<ModuleId>> adj = new HashMap<>();
        Map<ModuleId, Integer> inDegree = new HashMap<>();

        for (ModuleId id : byId.keySet()) {
            adj.put(id, new HashSet<>());
            inDegree.put(id, 0);
        }

        for (SkyModule module : modules) {
            ModuleId dependent = module.id();

            // Required dependencies
            for (ModuleId dep : module.metadata().dependencies()) {
                adj.get(dep).add(dependent);
                inDegree.put(dependent, inDegree.get(dependent) + 1);
            }

            // Optional dependencies (only add edge if optional dependency is present)
            for (ModuleId optDep : module.metadata().optionalDependencies()) {
                if (byId.containsKey(optDep)) {
                    adj.get(optDep).add(dependent);
                    inDegree.put(dependent, inDegree.get(dependent) + 1);
                }
            }
        }

        // 3. Kahn's Algorithm
        PriorityQueue<ModuleId> queue = new PriorityQueue<>();
        for (Map.Entry<ModuleId, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<SkyModule> resolved = new ArrayList<>();
        while (!queue.isEmpty()) {
            ModuleId current = queue.poll();
            resolved.add(byId.get(current));

            for (ModuleId neighbor : adj.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 4. Cycle detection
        if (resolved.size() != modules.size()) {
            List<ModuleId> cycle = detectCycle(adj, byId.keySet());
            throw new CyclicDependencyException(cycle);
        }

        return resolved;
    }

    private List<ModuleId> detectCycle(Map<ModuleId, Set<ModuleId>> adj, Set<ModuleId> allNodes) {
        Map<ModuleId, Integer> visited = new HashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        List<ModuleId> stack = new ArrayList<>();

        for (ModuleId node : allNodes) {
            visited.put(node, 0);
        }

        for (ModuleId node : allNodes) {
            if (visited.get(node) == 0) {
                List<ModuleId> cycle = dfsCycle(node, adj, visited, stack);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<ModuleId> dfsCycle(
            ModuleId current,
            Map<ModuleId, Set<ModuleId>> adj,
            Map<ModuleId, Integer> visited,
            List<ModuleId> stack
    ) {
        visited.put(current, 1);
        stack.add(current);

        for (ModuleId neighbor : adj.getOrDefault(current, Collections.emptySet())) {
            if (visited.get(neighbor) == 1) {
                // Cycle found
                int index = stack.indexOf(neighbor);
                List<ModuleId> cycle = new ArrayList<>(stack.subList(index, stack.size()));
                cycle.add(neighbor);
                return cycle;
            } else if (visited.get(neighbor) == 0) {
                List<ModuleId> cycle = dfsCycle(neighbor, adj, visited, stack);
                if (cycle != null) {
                    return cycle;
                }
            }
        }

        visited.put(current, 2);
        stack.remove(stack.size() - 1);
        return null;
    }
}
