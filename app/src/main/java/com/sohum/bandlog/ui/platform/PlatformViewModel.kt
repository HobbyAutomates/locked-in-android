package com.sohum.bandlog.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sohum.bandlog.data.Api
import com.sohum.bandlog.data.BodyMeasurement
import com.sohum.bandlog.data.InboxItem
import com.sohum.bandlog.data.PhotoV2
import com.sohum.bandlog.data.PlatformApi
import com.sohum.bandlog.data.PlatformProfile
import com.sohum.bandlog.data.SchemaMissingException
import com.sohum.bandlog.data.Workout
import com.sohum.bandlog.util.Dates
import com.sohum.bandlog.util.Pro
import com.sohum.bandlog.util.Routines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * v2.13 platform state shared by every page in this half (one instance per activity, like
 * AppViewModel): the Pro plan, the inbox, body measurements, progress photos, routines, the full
 * lift history for PR charts, and the live workout in progress.
 *
 * Each v36-backed list has a `…Supported` flag: null = not loaded yet, false = the table isn't
 * there (the page says "Coming with the next update"), true = live.
 */
class PlatformViewModel : ViewModel() {
    var extras by mutableStateOf<PlatformProfile?>(null); private set
    var proConfig by mutableStateOf(Pro.DEFAULT_CONFIG); private set
    /** Pro unless the plan column exists and says otherwise (everyone is on 'beta' today). */
    val hasPro: Boolean get() = extras?.hasPro ?: true

    var error by mutableStateOf<String?>(null)

    private suspend fun <T> guard(onMissing: () -> Unit, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) { throw e }
    catch (e: SchemaMissingException) { onMissing(); null }
    catch (e: Exception) { error = e.message ?: "Something went wrong"; null }

    fun loadPlan() {
        viewModelScope.launch {
            runCatching { extras = PlatformApi.profileExtras() }
            proConfig = PlatformApi.proConfig()
        }
    }

    // ---- inbox ----

    var inbox by mutableStateOf<List<InboxItem>>(emptyList()); private set
    var inboxSupported by mutableStateOf<Boolean?>(null); private set
    val unread: Int get() = inbox.count { it.unread }

    fun loadInbox() {
        viewModelScope.launch {
            val rows = guard({ inboxSupported = false }) { PlatformApi.inbox() } ?: return@launch
            inbox = rows; inboxSupported = true
        }
    }

    fun markRead(item: InboxItem) {
        if (!item.unread) return
        val now = java.time.Instant.now().toString()
        inbox = inbox.map { if (it.id == item.id) it.copy(readAt = now) else it }
        viewModelScope.launch { runCatching { PlatformApi.markRead(listOf(item.id)) } }
    }

    fun markAllRead() {
        val now = java.time.Instant.now().toString()
        inbox = inbox.map { if (it.unread) it.copy(readAt = now) else it }
        viewModelScope.launch { runCatching { PlatformApi.markAllRead() } }
    }

    // ---- body measurements ----

    var measurements by mutableStateOf<List<BodyMeasurement>>(emptyList()); private set
    var measurementsSupported by mutableStateOf<Boolean?>(null); private set
    /** The newest waist entry (the BMI card uses it before profiles.waist_cm). */
    val latestWaist: Double? get() = measurements.firstOrNull { it.get("waist_cm") != null }?.get("waist_cm")

    fun loadMeasurements() {
        viewModelScope.launch {
            val rows = guard({ measurementsSupported = false }) { PlatformApi.measurements() } ?: return@launch
            measurements = rows; measurementsSupported = true
        }
    }

    suspend fun saveMeasurement(m: BodyMeasurement): Boolean {
        val ok = guard({ measurementsSupported = false }) { PlatformApi.saveMeasurement(m); true } == true
        if (ok) runCatching { measurements = PlatformApi.measurements() }
        return ok
    }

    /** Optimistic: gone from the list now; [commitDeleteMeasurement] after the Undo window. */
    fun hideMeasurement(id: String) { measurements = measurements.filter { it.id != id } }
    fun commitDeleteMeasurement(id: String) { viewModelScope.launch { guard({}) { PlatformApi.deleteMeasurement(id) } } }
    fun reloadMeasurements() = loadMeasurements()

    // ---- progress photos ----

    var photos by mutableStateOf<List<PhotoV2>>(emptyList()); private set
    var photosLoaded by mutableStateOf(false); private set
    /** False when the v36 photo columns (weight, pose) aren't there: those fields are hidden. */
    var photoExtrasSupported by mutableStateOf(true); private set

    fun loadPhotos() {
        viewModelScope.launch {
            val rows = guard({}) { PlatformApi.photos() } ?: return@launch
            photos = rows; photosLoaded = true
            if (rows.isNotEmpty()) photoExtrasSupported = true
        }
    }

    suspend fun uploadPhoto(bmp: android.graphics.Bitmap, date: String, note: String, weightKg: Double?, pose: String?): Boolean {
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.sohum.bandlog.util.Images.jpeg(com.sohum.bandlog.util.Images.fitWithin(bmp, 1024), 85)
        }
        val ok = guard({}) { PlatformApi.uploadPhoto(bytes, date, note, weightKg, pose); true } == true
        if (ok) loadPhotos()
        return ok
    }

    suspend fun updatePhoto(ph: PhotoV2): Boolean {
        val full = guard({}) { PlatformApi.updatePhoto(ph.id, ph.date, ph.note, ph.weightKg, ph.pose) } ?: return false
        if (!full) photoExtrasSupported = false
        photos = photos.map { if (it.id == ph.id) (if (full) ph else ph.copy(weightKg = it.weightKg, pose = it.pose)) else it }.sortedByDescending { it.date }
        return true
    }

    fun hidePhoto(id: String) { photos = photos.filter { it.id != id } }
    /** After the Undo window: drop it from the list, then delete the row and the Storage object. */
    fun commitDeletePhoto(ph: PhotoV2) { hidePhoto(ph.id); viewModelScope.launch { guard({}) { PlatformApi.deletePhoto(ph.id, ph.path) } } }

    /** "Share to squad" (only after the user confirmed): re-uploads to group-photos and posts a 'photo' post. */
    suspend fun sharePhotoToSquad(ph: PhotoV2, caption: String): Int? = guard({}) {
        val bytes = PlatformApi.storageBytes("progress-photos", ph.path) ?: throw IllegalStateException("Couldn't read that photo")
        val path = Api.uploadGroupPhoto(bytes)
        val body = caption.ifBlank { "Progress check-in" }
        runCatching { Api.postToMyGroups("photo", body, ph.id, path) }.getOrElse {
            val groups = Api.mySquadIds()
            Api.postToGroups(groups, "photo", body, ph.id, path)
            groups.size
        }
    }

    // ---- routines ----

    var routines by mutableStateOf<List<Routines.Routine>>(emptyList()); private set
    var routinesSupported by mutableStateOf<Boolean?>(null); private set
    val activeRoutine: Routines.Routine? get() = routines.firstOrNull { it.active }

    fun loadRoutines() {
        viewModelScope.launch {
            val rows = guard({ routinesSupported = false }) { PlatformApi.routines() } ?: return@launch
            routines = rows; routinesSupported = true
        }
    }

    suspend fun saveRoutine(r: Routines.Routine): Boolean {
        val ok = guard({ routinesSupported = false }) { PlatformApi.saveRoutine(r) } != null
        if (ok) runCatching { routines = PlatformApi.routines() }
        return ok
    }

    fun setActive(id: String?) {
        routines = routines.map { it.copy(active = it.id == id) }
        viewModelScope.launch { guard({ routinesSupported = false }) { PlatformApi.setActiveRoutine(id) }; runCatching { routines = PlatformApi.routines() } }
    }

    fun deleteRoutine(id: String) {
        routines = routines.filter { it.id != id }
        viewModelScope.launch { guard({}) { PlatformApi.deleteRoutine(id) } }
    }

    // ---- full lift history (PR charts beyond AppViewModel's 120 days) ----

    var allWorkouts by mutableStateOf<List<Workout>?>(null); private set

    fun loadAllWorkouts() {
        viewModelScope.launch { runCatching { allWorkouts = Api.workouts("2000-01-01", Dates.today()) } }
    }

    // ---- live workout ----

    var live by mutableStateOf<LiveWorkout?>(null)
}

/** One set row in the live workout checklist. */
data class LiveSet(val kg: String = "", val reps: String = "", val done: Boolean = false)

data class LiveExercise(val name: String, val restS: Int, val sets: List<LiveSet>)

/** The workout in progress. [restEndsAt] is the wall-clock end of the current rest (null = not resting). */
data class LiveWorkout(
    val title: String,
    val startedAt: Long,
    val exercises: List<LiveExercise>,
    val restEndsAt: Long? = null,
    val restTotalS: Int = 0,
    val restLabel: String = "",
    val restNext: String = "",
)
