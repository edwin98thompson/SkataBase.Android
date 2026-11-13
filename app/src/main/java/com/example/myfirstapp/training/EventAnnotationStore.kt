package com.example.myfirstapp.training

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

enum class TrickOutcome(val storageValue: String, val title: String) {
    SUCCESSFUL("successful", "Landed"),
    UNSUCCESSFUL("unsuccessful", "Bailed"),
    UNKNOWN("unknown", "Unsure")
}

data class EventAnnotation(
    val eventId: String,
    val sessionUri: String,
    val sourceFileName: String,
    val sourceUri: String,
    val startTimestampMs: Long,
    val durationMs: Long,
    val classificationKey: String,
    val outcome: TrickOutcome,
    val classifiedCopyUri: String,
    val updatedAtMs: Long
)

class EventAnnotationStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ANNOTATIONS (
                $COLUMN_EVENT_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_SESSION_URI TEXT NOT NULL,
                $COLUMN_SOURCE_NAME TEXT NOT NULL,
                $COLUMN_SOURCE_URI TEXT NOT NULL,
                $COLUMN_START_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_DURATION INTEGER NOT NULL,
                $COLUMN_CLASSIFICATION TEXT NOT NULL,
                $COLUMN_OUTCOME TEXT NOT NULL,
                $COLUMN_COPY_URI TEXT NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX annotations_session_idx ON $TABLE_ANNOTATIONS ($COLUMN_SESSION_URI)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add schema migrations here when the annotation model changes.
    }

    fun annotationsForSession(sessionUri: Uri): Map<String, EventAnnotation> {
        val annotations = mutableMapOf<String, EventAnnotation>()
        readableDatabase.query(
            TABLE_ANNOTATIONS,
            null,
            "$COLUMN_SESSION_URI = ?",
            arrayOf(sessionUri.toString()),
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val annotation = cursor.toAnnotation()
                annotations[annotation.sourceFileName] = annotation
            }
        }
        return annotations
    }

    fun annotationForEvent(eventId: String): EventAnnotation? =
        readableDatabase.query(
            TABLE_ANNOTATIONS,
            null,
            "$COLUMN_EVENT_ID = ?",
            arrayOf(eventId),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAnnotation() else null
        }

    fun save(annotation: EventAnnotation): Boolean {
        val values = ContentValues().apply {
            put(COLUMN_EVENT_ID, annotation.eventId)
            put(COLUMN_SESSION_URI, annotation.sessionUri)
            put(COLUMN_SOURCE_NAME, annotation.sourceFileName)
            put(COLUMN_SOURCE_URI, annotation.sourceUri)
            put(COLUMN_START_TIMESTAMP, annotation.startTimestampMs)
            put(COLUMN_DURATION, annotation.durationMs)
            put(COLUMN_CLASSIFICATION, annotation.classificationKey)
            put(COLUMN_OUTCOME, annotation.outcome.storageValue)
            put(COLUMN_COPY_URI, annotation.classifiedCopyUri)
            put(COLUMN_UPDATED_AT, annotation.updatedAtMs)
        }
        return writableDatabase.insertWithOnConflict(
            TABLE_ANNOTATIONS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        ) != -1L
    }

    private fun android.database.Cursor.toAnnotation(): EventAnnotation {
        val outcomeValue = getString(getColumnIndexOrThrow(COLUMN_OUTCOME))
        return EventAnnotation(
            eventId = getString(getColumnIndexOrThrow(COLUMN_EVENT_ID)),
            sessionUri = getString(getColumnIndexOrThrow(COLUMN_SESSION_URI)),
            sourceFileName = getString(getColumnIndexOrThrow(COLUMN_SOURCE_NAME)),
            sourceUri = getString(getColumnIndexOrThrow(COLUMN_SOURCE_URI)),
            startTimestampMs = getLong(getColumnIndexOrThrow(COLUMN_START_TIMESTAMP)),
            durationMs = getLong(getColumnIndexOrThrow(COLUMN_DURATION)),
            classificationKey = getString(getColumnIndexOrThrow(COLUMN_CLASSIFICATION)),
            outcome = TrickOutcome.entries.firstOrNull { it.storageValue == outcomeValue }
                ?: TrickOutcome.UNKNOWN,
            classifiedCopyUri = getString(getColumnIndexOrThrow(COLUMN_COPY_URI)),
            updatedAtMs = getLong(getColumnIndexOrThrow(COLUMN_UPDATED_AT))
        )
    }

    companion object {
        private const val DATABASE_NAME = "event_annotations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_ANNOTATIONS = "event_annotations"
        private const val COLUMN_EVENT_ID = "event_id"
        private const val COLUMN_SESSION_URI = "session_uri"
        private const val COLUMN_SOURCE_NAME = "source_file_name"
        private const val COLUMN_SOURCE_URI = "source_uri"
        private const val COLUMN_START_TIMESTAMP = "start_timestamp_ms"
        private const val COLUMN_DURATION = "duration_ms"
        private const val COLUMN_CLASSIFICATION = "classification_key"
        private const val COLUMN_OUTCOME = "outcome"
        private const val COLUMN_COPY_URI = "classified_copy_uri"
        private const val COLUMN_UPDATED_AT = "updated_at_ms"
    }
}
