# Session 40 — Offline Campus Pathfinder Foundation

## What this session adds

Punla now has a safe foundation for offline, campus-aware walking routes without removing the existing OSRM or straight-line fallbacks.

Routing order:

1. `campus_waypoints.json` surveyed graph, when present and both endpoints are within coverage;
2. existing OSRM walking route;
3. caller's existing straight-line fallback if both are unavailable.

The package intentionally ships **only** `campus_waypoints.example.json`. The local graph stays dormant until real traced campus paths are supplied as `campus_waypoints.json`.

## New data-layer files

- `CampusPathGraph.kt`
  - validated node/edge graph model;
  - duplicate-node detection;
  - coordinate and edge-distance validation;
  - optional measured edge distances;
  - JSON parser and Android asset loader.
- `CampusPathfinder.kt`
  - offline A* routing;
  - nearest-edge endpoint snapping;
  - 60 m default coverage guard;
  - correct splitting when **both endpoints land on the same edge**;
  - splitting uses the graph's resolved/measured edge distance rather than recomputing a different weight.
- `CampusRoutingResolver.kt`
  - one shared offline-first route entry point;
  - caches the graph once per process;
  - silently stays disabled when `campus_waypoints.json` is absent;
  - falls back to the existing `fetchWalkingRoute()` OSRM implementation.

## Integration

Single-route fetches in these screens are routed through the shared resolver:

- Dashboard next-class route
- Campus Map sequential route-plan legs
- Campus Full Map next-class route

The multi-stop ordering matrix deliberately stays on OSRM for this first pass. A future pass can build an all-pairs local matrix when graph coverage is mature.

## Student Context / Today consistency

`StudentContextReducer` receives the same cached campus graph from `StudentContextEngine`. When a fresh location and a mapped next-class building are available, the travel buffer uses the local surveyed route distance first. If the graph is absent or that query is outside coverage, it retains the existing straight-line walking estimate.

This keeps Phase 2's `LEAVE SOON` logic aligned with the route the map will display once real campus data is installed.

## Important data rule

Do **not** rename `campus_waypoints.example.json` to `campus_waypoints.json`. Its coordinates are placeholder building centroids connected by a straight line. It exists only to document the schema.

Real graph data should be built from walked GPX tracks or carefully traced paths, with nodes at junctions, gates, bridge ends, and building entrances. Shared junctions must be merged into the same node ids.

## Survey helper

`tools/gpx_to_graph.py` converts a GPX 1.1 track into Punla node/edge JSON. It intentionally leaves shared-junction merging and path thinning as a manual review step.

## Tests

`CampusPathfinderTest.kt` covers:

- two endpoints snapped to different positions on the same edge;
- measured/resolved edge distance used during splitting;
- a bridge-chain route traversing the required crossing;
- queries outside surveyed coverage returning null for fallback;
- duplicate graph node ids being rejected.

## Version

When layered on Session 39 / Punla 3.4.0 (versionCode 38), this becomes **Punla 3.4.1 / versionCode 39**.

The installer is cumulative: from the current green 3.2.1/36 baseline it first applies bundled Session 39, then Session 40.
