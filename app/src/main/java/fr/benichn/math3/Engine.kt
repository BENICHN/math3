package fr.benichn.math3

import android.util.Log
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.ObservableProperty
import fr.benichn.math3.types.callback.VCC
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.security.Security
import java.util.LinkedList
import java.util.Queue
import org.zeromq.ZMQ
import org.zeromq.ZContext
import org.zeromq.SocketType
import kotlin.coroutines.resume

class JobQueue {
    data class Item(val source: Any, val block: suspend CoroutineScope.() -> Unit)
    private val queue: Queue<Item> = LinkedList()
    private var currentJob: Pair<Any, Job>? = null
    val isComputing
        get() = currentJob != null
    fun contains(source: Any) = currentJob?.first == source || queue.any { it.source == source }
    private fun startJobs() {
        if (!isComputing) {
            queue.poll()?.apply {
                currentJob = source to MainScope().launch {
                    try {
                        block()
                    } finally {
                        currentJob = null
                        startJobs()
                    }
                }
            }
        }
    }
    fun enqueue(source: Any, block: suspend CoroutineScope.() -> Unit) =
        if (!contains(source)) {
            queue.offer(Item(source, block))
            startJobs()
            true
        } else false
    suspend fun abort(source: Any, abortAction: suspend (Job) -> Unit = { job -> job.cancelAndJoin() }) =
        if (currentJob?.first == source) {
            abortAction(currentJob!!.second)
            currentJob = null
            true
        } else {
            queue.removeIf { it.source == source }
            false
        }
    suspend fun abortAll(abortAction: suspend (Any, Job) -> Unit = { _, job -> job.cancelAndJoin() }) {
        queue.clear()
        currentJob?.run {
            abortAction(first, second)
            currentJob = null
        }
    }
}

abstract class Engine {
    private val jobQueue = JobQueue()

    private val notifyStatusChanged = VCC<Engine, Status>(this)
    val onStatusChanged = notifyStatusChanged.Listener()
    var status by
        ObservableProperty(this, Status.STOPPED).apply {
            onChanged += { _, e ->
                notifyStatusChanged(e)
            }
        }
        protected set

    // suspend fun waitForStatus(st: Status) = suspendCoroutine { cont ->
    //     if (status == st) {
    //         cont.resume(Unit)
    //     } else {
    //         onStatusChanged.add { _, e ->
    //             if (e.new == st) {
    //                 cont.resume(Unit)
    //                 true
    //             } else false
    //         }
    //     }
    // }

    suspend fun abortAll() {
        status = Status.ABORTING
        jobQueue.abortAll { _, job ->
            abortComputation(job)
        }
        status = Status.READY
    }

    suspend fun abort(source: Any) {
        status = Status.ABORTING
        jobQueue.abort(source) { job ->
            abortComputation(job)
        }
        status = Status.READY
    }

    fun contains(source: Any) = jobQueue.contains(source)

    fun enqueue(source: Any, command: String): Flow<String>? =
        if (!jobQueue.contains(source)) channelFlow {
            suspendCancellableCoroutine { cont ->
                jobQueue.enqueue(source) {
                    status = Status.COMPUTING
                    runCommand(command).collect { s ->
                        send(s)
                    }
                    cont.resume(Unit)
                    status = Status.READY
                }
            }
        } else null

    suspend fun start() {
        if (status == Status.STOPPED) {
            status = Status.STARTING
            startEngine()
            status = Status.READY
        }
    }

    suspend fun stop() {
        if (status != Status.STOPPED) {
            abortAll()
            status = Status.STOPPING
            stopEngine()
            status = Status.STOPPED
        }
    }

    protected open suspend fun abortComputation(computationJob: Job) = computationJob.cancelAndJoin()
    protected abstract fun runCommand(command: String): Flow<String>
    protected abstract suspend fun startEngine()
    protected abstract suspend fun stopEngine()

    enum class Status {
        STOPPED,
        STARTING,
        READY,
        ABORTING,
        COMPUTING,
        STOPPING
    }

    companion object {
        const val MAX_RESULT_LENGTH = 1000
    }
}

// sealed class CommandResult {
//     data class Success(val result: String): CommandResult()
//     data object Failure: CommandResult()
//     data object Canceled: CommandResult()
// }

class WolframEngine : Engine() {
    private var socket: ZMQ.Socket? = null

    private fun sendJson(vararg entries: Pair<String, String>): JSONObject {
        val j = JSONObject(mapOf(*entries))
        socket?.send(j.toString())
        return j
    }

    private suspend fun receiveResponses() {
        while (socket != null) {
            val resp = withContext(Dispatchers.IO) { socket!!.recvStr() }
            try {
                val rpj = JSONObject(resp)
                val handled = notifyReceivedResponse(rpj)
                if (!handled) {
                    Log.d("WolframEngine", "Response not handled: $rpj")
                }
                handleIgnored?.let { l ->
                    handleIgnored = null
                    l()
                }
            } catch (e: JSONException) {
                Log.d("WolframEngine", "Ignored response: $resp due to $e")
            }
        }
    }

    private val notifyReceivedResponse = Callback<WolframEngine, JSONObject>(this)
    private val onReceivedResponse = notifyReceivedResponse.Listener()

    private var handleIgnored: (() -> Unit)? = null

    private suspend fun JSONObject.waitForResponse(type: String): JSONObject {
        var handleResponse: ((Any, JSONObject) -> Boolean)? = null
        try {
            return suspendCancellableCoroutine { cont ->
                handleResponse = { _: Any, rpj: JSONObject ->
                    when (rpj.getString("type")) {
                        "ignored" -> {
                            val rqi = rpj.getJSONObject("request")
                            if (rqi == this) {
                                handleIgnored = {
                                    cont.cancel()
                                }
                                true
                            } else false
                        }
                        type -> {
                            cont.resume(rpj)
                            true
                        }
                        else -> false
                    }
                }
                onReceivedResponse.add(handleResponse!!)
            }
        } finally {
            handleResponse?.let { l -> onReceivedResponse.remove(l) }
        }
    }

    private var producerScope: ProducerScope<String>? = null

    override fun runCommand(command: String): Flow<String> {
        return channelFlow {
            producerScope = this
            val res = sendJson(
                "type" to "eval",
                "input" to command
            ).waitForResponse("result")
            send(res.getString("output"))
            producerScope = null
        }
    }

    override suspend fun abortComputation(computationJob: Job) {
        sendJson(
            "type" to "abort"
        )
    }

    override suspend fun startEngine() = coroutineScope {
        if (socket != null) return@coroutineScope
        socket = ZContext().createSocket(SocketType.DEALER).apply {
            connect("tcp://")
        }
        launch { receiveResponses() }
        sendJson(
            "type" to "ping"
        ).waitForResponse("pong")
        onReceivedResponse += { _, rpj ->
            when (rpj.getString("type")) {
                "ping" -> sendJson(
                    "type" to "pong"
                )
                "session_expired" -> {
                    socket = null
                }
                "message" -> {
                    producerScope?.trySend(
                        rpj.getString("output")
                    )
                }
            }
        }
    }

    override suspend fun stopEngine() {
        if (socket == null) return
        sendJson(
            "type" to "quit"
        ).waitForResponse("stopped")
        socket?.close()
        socket = null
    }
}

// class SymjaEngine : Engine() {
//     private var ev: ExprEvaluator? = null
//     val isStarted
//         get() = ev != null
//     override suspend fun run(command: String) = ev!!.run {
//         val expr = eval(command)
//     }
//
//     override suspend fun start() {
//         status = Status.STARTING
//         ev = ExprEvaluator()
//         status = Status.READY
//     }
//
//     override suspend fun reset() {
//         status = Status.RESTARTING
//         ev = ExprEvaluator()
//         status = Status.READY
//     }
//
//     override suspend fun stop() {
//         TODO("Not yet implemented")
//     }
//
// }