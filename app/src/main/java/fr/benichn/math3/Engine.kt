package fr.benichn.math3

import android.util.Log
import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.ObservableProperty
import fr.benichn.math3.types.callback.VCC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Command
import net.schmizz.sshj.connection.channel.direct.Signal
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.LinkedList
import java.util.Queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
                        Log.d("job", queue.size.toString())
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
    suspend fun abort(source: Any) =
        if (currentJob?.first == source) {
            currentJob!!.second.cancelAndJoin()
            currentJob = null
            true
        } else {
            queue.removeIf { it.source == source }
            false
        }
    suspend fun abortAll() {
        queue.clear()
        currentJob?.run {
            second.cancelAndJoin()
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
        jobQueue.abortAll()
        status = Status.READY
    }

    suspend fun abort(source: Any) {
        status = Status.ABORTING
        jobQueue.abort(source)
        status = Status.READY
    }

    fun contains(source: Any) = jobQueue.contains(source)

    suspend fun enqueue(source: Any, command: String): CommandResult = suspendCoroutine { cont -> // ! (if deja enqued)
        jobQueue.enqueue(source) {
            status = Status.COMPUTING
            val r = runCommand(command)
            status = Status.READY
            cont.resume(r)
        }
    }

    suspend fun start() {
        if (status == Status.STOPPED) {
            status = Status.STARTING
            startEngine()
            status = Status.READY
        }
    }

    suspend fun stop() {
        if (status != Status.STOPPED) {
            jobQueue.abortAll()
            status = Status.STOPPING
            stopEngine()
            status = Status.STOPPED
        }
    }

    protected abstract suspend fun runCommand(command: String): CommandResult
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
}

sealed class CommandResult {
    data class Success(val result: String): CommandResult()
    data class Failure(val message: String): CommandResult()
}

abstract class SSHEngine : Engine() {
    inner class ChannelInfo(
        val command: Command,
    ) {
        private val outputStream = command.outputStream
        private val inputStream = command.inputStream
        private val errStream = command.errorStream
        private val writer = outputStream.bufferedWriter()

        var isReady by ObservableProperty(this, false).apply {
            onChanged += { _, e ->
                if (e.new) {
                    notifyReady(tmpString)
                    tmpString = ""
                }
            }
        }

        private val notifyReady = Callback<ChannelInfo, String>(this)
        val onReady = notifyReady.Listener()
        private val notifyError = Callback<ChannelInfo, String>(this)
        val onError = notifyError.Listener()

        private var tmpString = ""
        private val tmpBytes = ByteArray(BUFFER_SIZE)

        init {
            CoroutineScope(Dispatchers.IO).launch {
                while (command.isOpen) {
                    var a = inputStream.available()
                    if (a > 0) {
                        var s = ""
                        while (a > 0) {
                            val i: Int = inputStream.read(tmpBytes, 0, BUFFER_SIZE)
                            if (i < 0) break
                            s += String(tmpBytes, 0, i)
                            a = inputStream.available()
                        }
                        val str = s.trimEnd()
                        if (str.isNotEmpty()) {
                            val j = str.indexOfLast { it == '\n' }
                                .let { if (it == -1) str.lastIndex else it }
                            val lastLine = str.substring(j).trimStart()
                            val res = str.substring(0, j).trim()
                            tmpString += res
                            isReady = promptRegex.matches(lastLine)
                        } else {
                            isReady = false
                        }
                    }
                    a = errStream.available()
                    if (a > 0) {
                        var s = ""
                        while (a > 0) {
                            val i: Int = errStream.read(tmpBytes, 0, BUFFER_SIZE)
                            if (i < 0) break
                            s = String(tmpBytes, 0, i)
                            a = errStream.available()
                        }
                        notifyError(s)
                    }
                    delay(INTERVAL)
                }
            }
        }

        suspend fun waitForReadyOrError() =
            suspendCoroutine { cont ->
                val handleReady = { _: Any, s: String ->
                    cont.resume(interpretSuccess(s))
                    true
                }
                val handleError = { _: Any, s: String ->
                    cont.resume(CommandResult.Failure(s))
                    true
                }
                onReady.add(handleReady)
                onReady += { _, _ -> onError.remove(handleError) }
                onError.add(handleError)
                onError += { _, _ -> onReady.remove(handleReady) }
            }

        suspend fun close() {
            interruptComputation(this)
            command.close()
        }

        suspend fun sendCommand(com: String) = withContext(Dispatchers.IO) {
            writer.write(com)
            writer.newLine()
            writer.flush()
        }

        suspend fun sendInterrupt() = withContext(Dispatchers.IO) {
            command.signal(Signal.KILL)
        }
    }

    abstract val program: String
    abstract val promptRegex: Regex
    open suspend fun interruptComputation(channelInfo: ChannelInfo) { }
    open fun transformInput(s: String) = s
    open fun interpretSuccess(s: String): CommandResult = CommandResult.Success(s)

    private val client = SSHClient().apply {
        addHostKeyVerifier(PromiscuousVerifier())
    }
    private var session: Session? = null
    private var channelInfo: ChannelInfo? = null
    val isChannelOpen
        get() = channelInfo != null

    private suspend fun closeChannel() {
        channelInfo?.close()
        channelInfo = null
    }

    private suspend fun openSession() =
        session ?: withContext(Dispatchers.IO) {
            client.connect("")
            client.authPassword("", "")
            client.startSession()
        }.also {
            session = it
        }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun openChanel() =
        channelInfo ?: ChannelInfo(session!!.exec(program)).also {
            channelInfo = it
        }

    override suspend fun stopEngine() {
        closeChannel()
        closeSession()
    }

    override suspend fun startEngine() {
        openSession()
        openChanel().apply {
            waitForReadyOrError()
        }
    }

    override suspend fun runCommand(command: String) = channelInfo!!.run {
        sendCommand(transformInput(command))
        waitForReadyOrError()
    }

    // override suspend fun run(command: String): CommandResult = channelInfo!!.run {
    //     sendCommand(command)
    //     waitForReadyOrError()
    // }
//
    // override suspend fun abort() {
    //     channelInfo?.let { ci ->
    //         interruptComputation(ci)
    //         waitForReadyOrError()
    //     }
    // }
//
    // override suspend fun start() {
    //     openSession()
    //     reset()
    // }
//
    // override suspend fun reset() {
    //     openChanel()
    //     waitForReadyOrError()
    // }
//
    // override suspend fun stop() {
    //     closeSession()
    // }

    companion object {
        private fun setupBouncyCastle() {
            val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                ?: // Web3j will set up the provider lazily when it's first used.
                return
            if (provider.javaClass == BouncyCastleProvider::class.java) {
                // BC with same package name, shouldn't happen in real life.
                return
            }
            // Android registers its own BC provider. As it might be outdated and might not include
            // all needed ciphers, we substitute it with a known BC bundled in the app.
            // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
            // of that it's possible to have another BC implementation loaded in VM.
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        init {
            setupBouncyCastle()
        }
        const val BUFFER_SIZE = 1048576
        const val INTERVAL = 100L
    }
}

class WolframEngine : SSHEngine() {
    override val program = "wolframscript"
    override val promptRegex = Regex("""^In\[\d+]:=$""")
    val outRegex = Regex("""^Out\[\d+]//InputForm= """)
    override suspend fun interruptComputation(channelInfo: ChannelInfo) {
        channelInfo.sendInterrupt()
        channelInfo.sendCommand("a")
    }

    override fun interpretSuccess(s: String): CommandResult {
        val r = outRegex.replace(s, "")
        return if (r.length < s.length) CommandResult.Success(r) else CommandResult.Failure(s)
    }

    override fun transformInput(s: String) = "InputForm[$s]"
}

class SageEngine : SSHEngine() {
    override val program = "sage"
    override val promptRegex = Regex("""^sage:$""")
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