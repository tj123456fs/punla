package com.uplb.punla.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii taken directly from index.html's <style> block:
//   extraSmall -> input/select/.badge corner treatments etc.        (8px cluster -> 6dp kept as a slightly tighter chip radius)
//   small      -> button.primary / button.ghost / .switch base       (10px)
//   medium     -> .card / .stat-box                                 (14px / 12px cluster)
//   large      -> .day-pill / .badge / .dl-count pill shapes         (20px)
//   extraLarge -> AlertDialog surfaces; the web app has no true modal
//                 dialog surface except .map-modal-panel's top corners
//                 (18px 18px 0 0), which is the closest real "dialog"
//                 radius in the source, so Material3's AlertDialog
//                 (which defaults to shapes.extraLarge) uses that value
//                 instead of Material's stock 28dp.
val PunlaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
