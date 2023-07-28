package fr.benichn.math3

import fr.benichn.math3.types.callback.Callback
import fr.benichn.math3.types.callback.ObservableProperty
import fr.benichn.math3.types.callback.VCC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Command
import net.schmizz.sshj.connection.channel.direct.Signal
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


abstract class Engine {
    private val notifyStatusChanged = VCC<Engine, Status>(this)
    val onStatusChanged = notifyStatusChanged.Listener()
    var status by
        ObservableProperty(this, Status.STOPPED).apply {
            onChanged += { _, e ->
                notifyStatusChanged(e)
            }
        }
        protected set

    suspend fun waitForStatus(st: Status) = suspendCoroutine { cont ->
        if (status == st) {
            cont.resume(Unit)
        } else {
            onStatusChanged.add { _, e ->
                if (e.new == st) {
                    cont.resume(Unit)
                    true
                } else false
            }
        }
    }

    abstract suspend fun run(command: String): CommandResult
    open suspend fun abort() {
        reset()
    }
    abstract suspend fun start()
    abstract suspend fun reset()
    abstract suspend fun stop()

    enum class Status {
        STOPPED,
        STARTING,
        RESTARTING,
        READY,
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
                checkInput()
            }
        }

        private suspend fun checkInput() {
            while (command.isOpen) {
                // Log.d("fr", "ah")
                var a = inputStream.available()
                if (a > 0) {
                    var s = ""
                    while (a > 0) {
                        val i: Int = inputStream.read(tmpBytes, 0, BUFFER_SIZE)
                        if (i < 0) break
                        s += String(tmpBytes, 0, i)
                        a = inputStream.available()
                    }
                    // Log.d("in", s)
                    // Log.d("in", "debu")
                    val str = s.trimEnd()
                    if (str.isNotEmpty()) {
                        val j = str.indexOfLast { it == '\n' }.let { if (it == -1) str.lastIndex else it }
                        val lastLine = str.substring(j).trimStart()
                        val res = str.substring(0, j).trim()
                        tmpString += res
                        isReady = promptRegex.matches(lastLine)
                    } else {
                        isReady = false
                    }
                    // Log.d("in", "f1")
                }
                a = errStream.available()
                if (a > 0) {
                    var s = ""
                    while (a > 0) {
                        val i: Int = errStream.read(tmpBytes, 0, BUFFER_SIZE)
                        if (i < 0) break
                        s = String(tmpBytes, 0, i)
                        // Log.d("er", s)
                        a = errStream.available()
                    }
                    notifyError(s)
                }
                delay(INTERVAL)
            }
        }

        suspend fun close() {
            interruptComputation(this)
            command.close()
        }

        fun runCom(com: String) {
            writer.write(com)
            writer.newLine()
            writer.flush()
        }

        fun sendInterrupt() {
            command.signal(Signal.KILL)
        }
    }

    abstract val program: String
    abstract val promptRegex: Regex
    open suspend fun interruptComputation(channelInfo: ChannelInfo) = reset()

    private val client = SSHClient().apply {
        addHostKeyVerifier(PromiscuousVerifier())
    }
    private var session: Session? = null
    val isSessionOpen
        get() = session != null
    private var channelInfo: ChannelInfo? = null
    val isChannelOpen
        get() = channelInfo != null

    protected suspend fun closeChannel() {
        channelInfo?.close()
        channelInfo = null
    }

    protected suspend fun openSession(): Session {
        if (isSessionOpen) {
            closeSession()
        }
        status = Status.STARTING
        // Log.d("jsch", "op")
        client.connect("192.168.1.60")
        client.authPassword("", "")
        return client.startSession().also {
            session = it
        }
    }

    protected suspend fun closeSession() {
        status = Status.STOPPING
        closeChannel()
        session?.close()
        session = null
        status = Status.STOPPED
    }

    protected suspend fun openChanel(): ChannelInfo {
        // Log.d("jsch", "ch")
        if (isChannelOpen) {
            closeChannel()
        }
        return ChannelInfo(session!!.exec(program)).also {
            channelInfo = it
        }
    }

    protected suspend fun waitForReadyOrError(): CommandResult = channelInfo!!.run {
        suspendCoroutine { cont ->
            val handleReady = { _: Any, s: String ->
                status = Status.READY
                cont.resume(CommandResult.Success(s))
                true
            }
            val handleError = { _: Any, s: String ->
                status = Status.READY
                cont.resume(CommandResult.Failure(s))
                true
            }
            onReady.add(handleReady)
            onReady += { _, _ -> onError.remove(handleError) }
            onError.add(handleError)
            onError += { _, _ -> onReady.remove(handleReady) }
        }
    }

    override suspend fun run(command: String): CommandResult = channelInfo!!.run {
        status = Status.COMPUTING
        runCom(command)
        waitForReadyOrError()
    }

    override suspend fun abort() {
        channelInfo?.let { ci ->
            interruptComputation(ci)
            waitForReadyOrError()
        }
    }

    override suspend fun start() {
        openSession()
        reset()
    }

    override suspend fun reset() {
        if (status != Status.STARTING) status = Status.RESTARTING
        openChanel()
        waitForReadyOrError()
    }

    override suspend fun stop() {
        closeSession()
    }

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
    override val program = "math"
    override val promptRegex = Regex("""^In\[\d+]:=$""")
    override suspend fun interruptComputation(channelInfo: ChannelInfo) {
        channelInfo.sendInterrupt()
        channelInfo.runCom("a")
    }
}

class SageEngine : SSHEngine() {
    override val program = "sage"
    override val promptRegex = Regex("""^sage:$""")
}