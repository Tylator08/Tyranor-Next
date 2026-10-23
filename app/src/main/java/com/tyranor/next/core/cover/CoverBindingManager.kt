package com.tyranor.next.core.cover

import android.content.Context
import com.tyranor.next.R
import com.tyranor.next.core.game.model.ScanGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 封面绑定任务：搜索弹窗关闭后继续下载，并在原游戏封面显示进度圈。
 * 点击进度圈可暂停/继续；暂停会取消当前下载，继续时从头发起。
 */
object CoverBindingManager {

    data class State(val paused: Boolean, val progress: Float = 0f)

    private data class Run(
        val cancelled: AtomicBoolean,
        val token: Any = Any(),
        var job: Job? = null,
    )

    private data class Task(
        val context: Context,
        val game: ScanGame,
        val candidate: CoverSearchCandidate,
        val onUpdated: (ScanGame) -> Unit,
    ) {
        var run: Run? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    private val tasks = ConcurrentHashMap<String, Task>()

    fun bind(
        context: Context,
        game: ScanGame,
        candidate: CoverSearchCandidate,
        onUpdated: (ScanGame) -> Unit,
    ) {
        tasks.remove(game.uri)?.run?.let {
            it.cancelled.set(true)
            it.job?.cancel()
        }
        val task = Task(context.applicationContext, game, candidate, onUpdated)
        tasks[game.uri] = task
        start(task)
    }

    fun toggle(uri: String) {
        val task = tasks[uri] ?: return
        val paused = _states.value[uri]?.paused ?: false
        if (paused) {
            start(task)
        } else {
            task.run?.let {
                it.cancelled.set(true)
                it.job?.cancel()
            }
            task.run = null
            _states.value = _states.value + (uri to State(true, _states.value[uri]?.progress ?: 0f))
        }
    }

    private fun start(task: Task) {
        val run = Run(AtomicBoolean(false))
        task.run = run
        _states.value = _states.value + (task.game.uri to State(false, 0.03f))
        run.job = scope.launch {
            var lastEmit = 0L
            val updated = try {
                CoverScraperService.bindCoverCandidate(
                    task.context,
                    task.game,
                    task.candidate,
                    isCancelled = { run.cancelled.get() },
                    onProgress = { progress ->
                        // 进度按 100ms 节流落到 StateFlow，避免每个读块都触发一次 UI 重组。
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 100L || progress >= 0.95f) {
                            lastEmit = now
                            val current = _states.value[task.game.uri]
                            if (current != null && !current.paused) {
                                _states.value = _states.value + (task.game.uri to current.copy(progress = progress))
                            }
                        }
                    },
                )
            } catch (_: Throwable) {
                null
            }
            if (run.cancelled.get()) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (task.run !== run) return@withContext
                if (updated != null) {
                    task.onUpdated(updated)
                } else {
                    android.widget.Toast.makeText(
                        task.context,
                        task.context.getString(R.string.game_cover_download_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                tasks.remove(task.game.uri, task)
                _states.value = _states.value - task.game.uri
            }
            // 旧封面删除移出绑定关键路径：UI 已拿到新封面后再清理缓存中的旧图。
            if (updated != null) {
                withContext(Dispatchers.IO) {
                    CoverImageCache.deleteCachedCover(task.context, task.game.coverUri, updated.coverUri)
                }
            }
        }
    }
}
