package org.fossify.gallery.aes

import AESDecryptWorker
import android.app.Activity
import android.content.Context
import android.content.DialogInterface.OnDismissListener
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.App
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import kotlinx.coroutines.*
import java.util.*


fun <T> debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}

fun <T> throttleLatest(
    intervalMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    var latestParam: T
    return { param: T ->
        latestParam = param
        if (throttleJob?.isCompleted != false) {
            throttleJob = coroutineScope.launch {
                delay(intervalMs)
                latestParam.let(destinationFunction)
            }
        }
    }
}

fun <T> throttleFirst(
    skipMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    return { param: T ->
        if (throttleJob?.isCompleted != false) {
            throttleJob = coroutineScope.launch {
                destinationFunction(param)
                delay(skipMs)
            }
        }
    }
}

fun <T> throttleFirstLast(
    skipMs: Long = 300L,
    coroutineScope: CoroutineScope,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var throttleJob: Job? = null
    var lastParam: T? = null

    fun addJob() {
        if (lastParam != null) {
            val param = lastParam!!
            lastParam = null
            throttleJob = coroutineScope.launch {
                destinationFunction(param)
                delay(skipMs)
            }.also { job ->
                job.invokeOnCompletion {
                    //linePrint(" job completion $it $lastParam")
                    addJob()
                }
            }
        }
    }

    return { param: T ->
        //   linePrint("setting up throttle ${throttleJob?.isCompleted}")
        lastParam = param
        if (throttleJob?.isCompleted != false) {
            addJob()
        }
    }
}

data class TaskModel(
    var pending: Int = 0,
    var started: Int = 0,
    var completed: Int = 0,
    var total: Int = 0,
    var failed: Int = 0,
    var list: ArrayList<AESTaskInfo> = ArrayList()
)

class AESTasker(val listener: ProgressCallback) {
    private val maxItems = 3
    private var mDialog: AESProgressDialog? = null
    val mTaskMap = ConcurrentLinkedHashMap.Builder<String, AESTaskInfo>().initialCapacity(32).maximumWeightedCapacity(20000).build()
    private val progressComp = Comparator<String> { o1, o2 -> getProgress(o1).compareTo(getProgress(o2)) }
    private val mCompletedCount = 0

    private fun getProgress(key: String): Int {
        return mTaskMap[key]?.progress ?: 0
    }

    fun showView(activity: SimpleActivity) {
        if (mDialog == null) {
            mDialog = AESProgressDialog(activity) { mDialog = null }
        }
        mDialog!!.show()
    }

    fun hideView() {
        mDialog?.cancel()
    }

    private fun notifyChangeImpl() {
        val keys = mTaskMap.keys.toTypedArray()
        val completed = ArrayList<String>()
        val model = TaskModel()
        model.total = mTaskMap.size
        var hasEncrypt = false
        keys.forEach { key ->
            val value = mTaskMap[key]
            value?.let { task ->
                if (task.state == AESTaskState.PENDING) {
                    model.pending++
                    return@let
                }
                if (task.isCompleted()) {
                    hasEncrypt = task.type == AESTaskType.ENCRYPT
                    completed.add(key)
                    if (task.isSucceeded()) {
                        model.completed++
                    } else {
                        model.failed++
                    }
                    return@let
                }
                model.started++
                model.list.add(task)
            }
        }
        if (completed.size == mTaskMap.size) {
            mTaskMap.clear()
        }
        val intent = Intent()
        intent.action = AES_TASK_UPDATE
        intent.putExtra(AES_TASK_COMPLETE_COUNT, completed.size)
        intent.putExtra("hasEncrypt", hasEncrypt)
        LocalBroadcastManager.getInstance(App.instance).sendBroadcast(intent)
        updateView(model)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifyChange = throttleFirstLast<Unit>(100, CoroutineScope(Dispatchers.Default.limitedParallelism(1))) {
        linePrint("nofify change")
        ensureBackgroundThread { notifyChangeImpl() }
    }

    fun enqueueTask(task: AESTaskInfo): Boolean {
        mTaskMap[task.id]?.let {
            if (!it.isCompleted()) return false
            mTaskMap.remove(task.id)
        }
        task.reset()
        if (enqueueWMTask(task)) {
            mTaskMap[task.id] = task
            notifyChange(Unit)
            return true
        }
        return false
    }

    private fun enqueueWMTask(task: AESTaskInfo): Boolean {
        return when (task.type) {
            AESTaskType.ENCRYPT -> {
                enqueueWMRequest<AESEncryptWorker>(task.id, ENCRYPT_WORKER_TAG)
                true
            }

            AESTaskType.DECRYPT -> {
                enqueueWMRequest<AESDecryptWorker>(task.id, DECRYPT_WORKER_TAG)
                true
            }
            else -> false
        }
    }

    private inline fun <reified T : ListenableWorker> enqueueWMRequest(id: String, tag: String) {
        // linePrint("starting work for $id")
        val inputData = Data.Builder().putString("taskId", id).build()

        val workRequest = OneTimeWorkRequestBuilder<T>()
            .addTag(tag)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(App.instance).beginUniqueWork(id, ExistingWorkPolicy.KEEP, workRequest).enqueue()
    }

    fun setProgress(taskId: String, progress: Int) {
        mTaskMap[taskId]?.let {
            it.state = if (progress < 0 || progress == 100) AESTaskState.COMPLETED else AESTaskState.STARTED
            it.status = if (progress < 0) AESTaskStatus.FAILED else if (progress == 100) AESTaskStatus.OK else AESTaskStatus.RUNNING
            it.progress = if (progress < 0) 0 else progress
            notifyChange(Unit)
            listener.onProgress(taskId, progress)
        }
    }

    fun getTask(taskId: String): AESTaskInfo? {
        return mTaskMap[taskId]
    }

    private fun updateView(model: TaskModel) {
        mDialog?.updateView(model)
    }
}

class AESProgressDialog(val activity: Activity, dismissCallback: OnDismissListener) {
    private var dialog: AlertDialog
    private val mContainer: ViewGroup
    private val statusText: TextView

    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.aes_progress_container, null) as ViewGroup
        mContainer = view.findViewById<ViewGroup?>(R.id.progress_container).getChildAt(0) as ViewGroup
        statusText = view.findViewById(R.id.task_status)
        dialog = activity.getAlertDialogBuilder()
            .setTitle("Task Queue")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Done") { dialog, which -> dialog.dismiss() }
            .create()

        dialog.setOnDismissListener(dismissCallback)
    }

    fun updateView(model: TaskModel) {
        //linePrint("updating view model ${model.list.size}")
        activity.runOnUiThread {
            statusText.setText("Completed ${model.completed} of ${model.total} | Pending: ${model.pending}")
            if (model.list.size == 0) {
                mContainer.removeAllViews()
                mContainer.beGone()
                return@runOnUiThread
            }
            mContainer.beVisible()
            var task: AESTaskInfo? = null;
            var index = 0
            while (index < model.list.size || index < mContainer.childCount) {
                task = model.list.getOrNull(index)
                if (task != null && mContainer.childCount <= index) {
                    mContainer.addView(ProgressView(activity))
                }
                val child: ProgressView? = mContainer.getChildAt(index) as ProgressView?
                child?.let {
                    if (task != null) {
                        child.update(task.meta.displayName ?: task.meta.toPath, task.progress)
                        child.beVisible()
                    } else {
                        child.beGone()
                    }
                }
                index++
            }
        }

    }

    fun show() {
        dialog.show()
    }

    fun cancel() {
        dialog.dismiss()
    }
}

class ProgressView(context: Context) : LinearLayout(context) {

    val progressText: TextView
    val progressValue: TextView
    val progressBar: ProgressBar

    init {
        orientation = VERTICAL
        val view = View.inflate(context, R.layout.aes_progress_view, this)
        progressText = view.findViewById(R.id.progress_text)
        progressValue = view.findViewById(R.id.progress_value)
        progressBar = view.findViewById(R.id.progress_horizontal)
    }

    fun update(text: String, progress: Int) {
        progressText.setText(text)
        progressValue.setText("$progress%")
        progressBar.progress = progress
    }
}
