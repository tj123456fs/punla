#!/usr/bin/env python3
"""Convert one GPX track into Punla campus waypoint nodes/edges.

This intentionally does not auto-merge shared junctions across separate tracks.
After conversion, review the geometry, thin redundant points, and merge node ids
for physical junctions before combining tracks into campus_waypoints.json.
"""
from __future__ import annotations
import json
import math
import sys
import xml.etree.ElementTree as ET

NS = {"gpx": "http://www.topografix.com/GPX/1/1"}


def convert(gpx_path: str, prefix: str) -> dict:
    tree = ET.parse(gpx_path)
    nodes = []
    edges = []
    for segment in tree.getroot().findall(".//gpx:trkseg", NS):
        # A recording gap is not evidence of a walkable path between segments.
        prev_id = None
        for pt in segment.findall("gpx:trkpt", NS):
            lat, lon = float(pt.get("lat")), float(pt.get("lon"))
            if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
                raise ValueError("Invalid GPX coordinate")
            node_id = f"{prefix}_{len(nodes)}"
            nodes.append({"id": node_id, "lat": lat, "lon": lon})
            if prev_id is not None:
                edges.append({"from": prev_id, "to": node_id})
            prev_id = node_id
    if not nodes:
        raise ValueError("No GPX 1.1 trackpoints found")

    return {"bidirectional": True, "nodes": nodes, "edges": edges}


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: gpx_to_graph.py TRACK.gpx PREFIX")
    print(json.dumps(convert(sys.argv[1], sys.argv[2]), indent=2))
