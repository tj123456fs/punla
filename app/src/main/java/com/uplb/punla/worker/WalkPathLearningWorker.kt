package com.uplb.punla.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uplb.punla.data.LearnedCampusPathRepository
import com.uplb.punla.data.WalkLearningStatus

/** Learns local-only walking geometry after the user explicitly stops a recorded walk. */
class WalkPathLearningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID)
        return try {
            if (sessionId == null) {
                LearnedCampusPathRepository.processPendingCompletedWalks(
                    applicationContext,
                    limit = BACKFILL_LIMIT
                )
                Result.success()
            } else {
                when (LearnedCampusPathRepository.processCompletedWalk(applicationContext, sessionId)) {
                    WalkLearningStatus.NOT_READY -> Result.retry()
                    else -> Result.success()
                }
            }
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val KEY_SESSION_ID = "walk_session_id"
        private const val MAX_RETRIES = 2
        private const val BACKFILL_LIMIT = 100
        private const val BACKFILL_WORK_NAME = "learn-campus-walk-backfill"

        fun enqueue(context: Context, sessionId: String) {
            val request = OneTimeWorkRequestBuilder<WalkPathLearningWorker>()
                .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "learn-campus-walk-$sessionId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueBackfill(context: Context) {
            val request = OneTimeWorkRequestBuilder<WalkPathLearningWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                BACKFILL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
