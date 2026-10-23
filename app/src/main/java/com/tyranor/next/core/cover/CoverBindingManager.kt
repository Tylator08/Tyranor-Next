package com.tyranor.next.core.cover

import android.content.Context
import com.tyranor.next.core.game.model.ScanGame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 封面绑定任务：搜索弹窗关闭后继续下载，并在原游戏封面显示进度圈。
 * 点击进度圈可暂停/继续；暂停会断开当前下载，继续时从头发起。
 *
 * 线程约定：任务表与 [states] 的读改写都在 [lock] 内完成，进度回调只在「自己仍是当前任务」时落盘，
 * 避免暂停/替换任务后旧协程把过期进度或状态写回来。
 * 失败只通过 [bind] 的 `onError` 上报，文案由 UI 层组装（core 不持有本地化文案）。
 */
object CoverBindingManager {

    data class State(val paused: Boolean, val progress: Float = 0f)

    private class Run(val cancel: CoverDownloadCancel) {
        var job: Job? = null
    }

    private class Task(
        val context: Context,
        val game: ScanGame,
        val candidate: CoverSearchCandidate,
        val onUpdated: (ScanGame) -> Unit,
        val onError: () -> Unit,
    ) {
        var run: Run? = null
    }

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    private val tasks = ConcurrentHashMap<String, Task>()

    fun bind(
        context: Context,
        game: ScanGame,
        candidate: CoverSearchCandidate,
        onUpdated: (ScanGame) -> Unit,
        onError: () -> Unit = {},
    ) {
        val task = Task(context.applicationContext, game, candidate, onUpdated, onError)
        val previous = synchronized(lock) { tasks.put(game.uri, task) }
        previous?.run?.let { run ->
            run.cancel.cancel()
            run.job?.cancel()
        }
        start(task)
    }

    fun toggle(uri: String) {
        val task = tasks[uri] ?: return
        val paused = _states.value[uri]?.paused ?: false
        if (paused) start(task) else pause(task)
    }

    private fun start(task: Task) {
        val run = Run(CoverDownloadCancel())
        synchronized(lock) {
            task.run = run
            _states.value = _states.value + (task.game.uri to State(paused = false, progress = 0f))
        }
        run.job = scope.launch {
            var lastEmit = 0L
            val updated = try {
                CoverScraperService.bindCoverCandidate(
                    task.context,
                    task.game,
                    task.candidate,
                    cancel = run.cancel,
                    onProgress = { progress ->
                        // 进度按 100ms 节流落到 StateFlow，避免每个读块都触发一次 UI 重组。
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 100L || progress >= 0.95f) {
                            lastEmit = now
                            synchronized(lock) {
                                // 暂停或换任务后旧协程仍可能回调，只有自己还是当前 run 才写入。
                                if (task.run !== run) return@synchronized
                                _states.value = _states.value +
                                    (task.game.uri to State(paused = false, progress = progress))
                            }
                        }
                    },
                )
            } catch (cancelled: CancellationException) {
                // 取消不是失败：原样向上抛，交给协程取消语义处理。
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (run.cancel.isCancelled) return@launch
            withContext(Dispatchers.Main.immediate) {
                val current = synchronized(lock) {
                    if (tasks[task.game.uri] !== task || task.run !== run) {
                        false
                    } else {
                        task.run = null
                        tasks.remove(task.game.uri, task)
                        _states.value = _states.value - task.game.uri
                        true
                    }
                }
                if (!current) return@withContext
                if (updated != null) task.onUpdated(updated) else task.onError()
            }
            // 旧封面删除移出绑定关键路径：UI 已拿到新封面后再清理缓存中的旧图。
            if (updated != null) {
                withContext(Dispatchers.IO) {
                    CoverImageCache.deleteCachedCover(task.context, task.game.coverUri, updated.coverUri)
                }
            }
        }
    }

    private fun pause(task: Task) {
        val run = synchronized(lock) {
            task.run.also { task.run = null }
        } ?: return
        // 先断开连接再取消协程：阻塞中的 read 会立刻抛错退出，不必等满 readTimeout。
        run.cancel.cancel()
        run.job?.cancel()
        synchronized(lock) {
            val progress = _states.value[task.game.uri]?.progress ?: 0f
            _states.value = _states.value + (task.game.uri to State(paused = true, progress = progress))
        }
    }
}
