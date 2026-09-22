# Campus waypoint survey notes

The route engine is only as accurate as `campus_waypoints.json`.

Recommended workflow:

1. Walk important campus paths with a GPX logger at a short recording interval.
2. Keep nodes at real junctions, forks, gates, bridge ends, entrances, and meaningful turns.
3. Thin redundant points on straight segments.
4. Merge separately recorded tracks at shared physical junctions into the same node id.
5. Model chokepoints such as a bridge as explicit path segments so the graph cannot cross a barrier anywhere else.
6. Validate important origin/destination pairs by eye against the real walked path before expanding coverage.

Start with the paths Punla actually uses most for next-class travel rather than attempting to survey the whole campus at once.
