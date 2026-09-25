package com.uplb.punla.data

import android.content.Context
import androidx.room.withTransaction
import com.uplb.punla.data.entity.LearnedWalkSession
import com.uplb.punla.data.entity.WalkSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WalkLearningStatus {
    PROCESSED,
    ALREADY_PROCESSED,
    NOT_READY,
    MISSING
}

object LearnedCampusPathRepository {
    suspend fun processCompletedWalk(
        context: Context,
        sessionId: String
    ): WalkLearningStatus = withContext(Dispatchers.IO) {
        val db = PunlaDatabase.get(context.applicationContext)
        db.withTransaction {
            val learnedDao = db.learnedCampusPathDao()
            if (learnedDao.getProcessedSession(sessionId) != null) {
                return@withTransaction WalkLearningStatus.ALREADY_PROCESSED
            }

            val walkDao = db.walkRecordingDao()
            val session = walkDao.getSession(sessionId)
                ?: return@withTransaction WalkLearningStatus.MISSING
            if (session.status != WalkSessionStatus.COMPLETED) {
                return@withTransaction WalkLearningStatus.NOT_READY
            }

            val existingNodes = learnedDao.getAllNodes()
            val existingEdges = learnedDao.getAllEdges()
            val processedAt = System.currentTimeMillis()
            val result = LearnedCampusPathEngine.applyWalk(
                points = walkDao.getPoints(sessionId),
                existingNodes = existingNodes,
                existingEdges = existingEdges,
                observedAt = processedAt
            )

            val oldNodes = existingNodes.associateBy { it.id }
            val changedNodes = result.state.nodes.filter { oldNodes[it.id] != it }
            if (changedNodes.isNotEmpty()) learnedDao.upsertNodes(changedNodes)

            val oldEdges = existingEdges.associateBy { it.id }
            val changedEdges = result.state.edges.filter { oldEdges[it.id] != it }
            if (changedEdges.isNotEmpty()) learnedDao.upsertEdges(changedEdges)

            learnedDao.upsertProcessedSession(
                LearnedWalkSession(
                    sessionId = sessionId,
                    processedAt = processedAt,
                    acceptedPointCount = result.acceptedPointCount,
                    learnedEdgeCount = result.observedEdgeCount
                )
            )
            WalkLearningStatus.PROCESSED
        }
    }

    suspend fun loadTrustedGraph(context: Context): CampusPathGraph? = withContext(Dispatchers.IO) {
        val dao = PunlaDatabase.get(context.applicationContext).learnedCampusPathDao()
        val edges = dao.getTrustedEdges(LearnedCampusPathEngine.MIN_TRUSTED_OBSERVATIONS)
        if (edges.isEmpty()) return@withContext null
        LearnedCampusPathEngine.buildTrustedGraph(
            nodes = dao.getAllNodes(),
            edges = edges
        )
    }
}
