package com.SST.server_state_telemetry_client.data.remote

import com.goterl.lazysodium.SodiumAndroid
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully

/**
 * Noise_XX_25519_ChaChaPoly_BLAKE2b 이니시에이터(클라이언트) 구현.
 *
 * 서버 NoiseSession.cpp 와 1:1 대응. 표준 HKDF 대신 BLAKE2b keyed-hash 기반
 * 커스텀 HKDF 를 사용하므로 표준 Noise 라이브러리는 호환되지 않는다.
 *
 * 인스턴스는 연결마다 새로 생성해야 한다 (mutable 핸드셰이크 상태 보유).
 */
class NoiseSession {

    companion object {
        const val KEY_SIZE = 32
        const val MAC_SIZE = 16
        const val NONCE_SIZE = 12

        // SodiumAndroid 는 JNA Library 구현체 — native 메서드를 직접 노출한다.
        // 초기화는 한 번만 수행.
        private val sodium: SodiumAndroid by lazy { SodiumAndroid() }
    }

    // ── 대칭 상태 ────────────────────────────────────────────────────────────
    private val h = ByteArray(KEY_SIZE)
    private val ck = ByteArray(KEY_SIZE)
    private val k = ByteArray(KEY_SIZE)
    private var n: Long = 0

    // ── 전송 단계 키/nonce ────────────────────────────────────────────────────
    private val sendKey = ByteArray(KEY_SIZE)
    private val recvKey = ByteArray(KEY_SIZE)
    private var sendNonce: Long = 0
    private var recvNonce: Long = 0

    // 핸드셰이크 중 MSG2 에서 수신한 서버 임시 공개키 (MSG3 DH 에 재사용)
    private val hsSePub = ByteArray(KEY_SIZE)

    var ready = false
        private set

    // ── 대칭 상태 헬퍼 ──────────────────────────────────────────────────────

    private fun initializeSymmetric() {
        // len("Noise_XX_25519_ChaChaPoly_BLAKE2b") = 34 > 32 → h = BLAKE2b(name)
        val name = "Noise_XX_25519_ChaChaPoly_BLAKE2b".toByteArray(Charsets.US_ASCII)
        blake2b(h, name, null)
        h.copyInto(ck)
        k.fill(0)
        n = 0
    }

    // BLAKE2b(data, key?). key=null 이면 unkeyed. 반환값 0 = 성공.
    private fun blake2b(out: ByteArray, data: ByteArray, key: ByteArray?) {
        sodium.crypto_generichash(out, out.size, data, data.size.toLong(), key, key?.size ?: 0)
    }

    // h = BLAKE2b(h || data)
    private fun mixHash(data: ByteArray) {
        blake2b(h, h + data, null)
    }

    // temp = BLAKE2b(dh, key=ck); ck = BLAKE2b(0x01, key=temp); k = BLAKE2b(ck||0x02, key=temp); n=0
    private fun mixKey(dh: ByteArray) {
        val temp = ByteArray(KEY_SIZE)
        blake2b(temp, dh, ck)
        blake2b(ck, byteArrayOf(0x01), temp)
        blake2b(k, ck + byteArrayOf(0x02), temp)
        n = 0
    }

    // 12바이트 IETF nonce: [0,0,0,0] + counter(8바이트 LE)
    private fun buildNonce(counter: Long): ByteArray {
        val nonce = ByteArray(NONCE_SIZE)
        for (i in 0 until 8) nonce[4 + i] = ((counter shr (8 * i)) and 0xff).toByte()
        return nonce
    }

    // ChaCha20-Poly1305 암호화(AD=h), 이후 mixHash(ciphertext)
    private fun encryptAndHash(pt: ByteArray): ByteArray? {
        val nonce = buildNonce(n++)
        val ct = ByteArray(pt.size + MAC_SIZE)
        val ctLen = LongArray(1)
        val rc = sodium.crypto_aead_chacha20poly1305_ietf_encrypt(
            ct, ctLen, pt, pt.size.toLong(), h, h.size.toLong(), null, nonce, k
        )
        if (rc != 0) return null
        val ciphertext = ct.copyOf(ctLen[0].toInt())
        mixHash(ciphertext)
        return ciphertext
    }

    // ChaCha20-Poly1305 복호화(AD=h), 이후 mixHash(ciphertext)
    private fun decryptAndHash(ct: ByteArray): ByteArray? {
        val nonce = buildNonce(n++)
        val pt = ByteArray(ct.size - MAC_SIZE)
        val ptLen = LongArray(1)
        val rc = sodium.crypto_aead_chacha20poly1305_ietf_decrypt(
            pt, ptLen, null, ct, ct.size.toLong(), h, h.size.toLong(), nonce, k
        )
        if (rc != 0) return null
        mixHash(ct)
        return pt.copyOf(ptLen[0].toInt())
    }

    // temp = BLAKE2b("", key=ck); k1 = BLAKE2b(0x01, key=temp); k2 = BLAKE2b(k1||0x02, key=temp)
    // 이니시에이터: sendKey=k1, recvKey=k2
    private fun split(isInitiator: Boolean) {
        val temp = ByteArray(KEY_SIZE)
        blake2b(temp, ByteArray(0), ck)
        val k1 = ByteArray(KEY_SIZE)
        blake2b(k1, byteArrayOf(0x01), temp)
        val k2 = ByteArray(KEY_SIZE)
        blake2b(k2, k1 + byteArrayOf(0x02), temp)
        if (isInitiator) { k1.copyInto(sendKey); k2.copyInto(recvKey) }
        else             { k2.copyInto(sendKey); k1.copyInto(recvKey) }
    }

    // ── 핸드셰이크 ───────────────────────────────────────────────────────────

    /**
     * Noise XX 이니시에이터 핸드셰이크.
     * 서버의 정적 공개키가 pinnedServerPub 과 다르면 false 반환.
     */
    suspend fun handshakeClient(
        readChannel: ByteReadChannel,
        writeChannel: ByteWriteChannel,
        pinnedServerPub: ByteArray
    ): Boolean {
        initializeSymmetric()

        // MSG1: → e (32B 평문)
        val ePub = ByteArray(KEY_SIZE)
        val ePriv = ByteArray(KEY_SIZE)
        sodium.crypto_box_keypair(ePub, ePriv)
        mixHash(ePub)
        writeChannel.writeFully(ePub)
        writeChannel.flush()

        // MSG2: ← e, ee, s, es (80B = se_pub:32 + encrypted_s:48)
        val msg2 = ByteArray(KEY_SIZE * 2 + MAC_SIZE)
        readChannel.readFully(msg2)

        val sePub = msg2.copyOf(KEY_SIZE)
        mixHash(sePub)
        sePub.copyInto(hsSePub)

        val dhEe = ByteArray(KEY_SIZE)
        if (sodium.crypto_scalarmult(dhEe, ePriv, sePub) != 0) return false
        mixKey(dhEe)

        val sRecv = decryptAndHash(msg2.copyOfRange(KEY_SIZE, msg2.size)) ?: return false
        if (!sRecv.contentEquals(pinnedServerPub)) return false  // MITM 차단

        val dhEs = ByteArray(KEY_SIZE)
        if (sodium.crypto_scalarmult(dhEs, ePriv, sRecv) != 0) return false
        mixKey(dhEs)

        // MSG3: → s, se (48B = encrypted_c_pub)
        val cPub = ByteArray(KEY_SIZE)
        val cPriv = ByteArray(KEY_SIZE)
        sodium.crypto_box_keypair(cPub, cPriv)

        val encryptedC = encryptAndHash(cPub) ?: return false

        val dhSe = ByteArray(KEY_SIZE)
        if (sodium.crypto_scalarmult(dhSe, cPriv, hsSePub) != 0) return false
        mixKey(dhSe)

        writeChannel.writeFully(encryptedC)
        writeChannel.flush()

        split(true)
        sendNonce = 0
        recvNonce = 0
        ready = true
        return true
    }

    // ── 전송 단계 복호화 ──────────────────────────────────────────────────────

    /** 전송 단계 ChaCha20-Poly1305 복호화 (AD 없음). 실패 시 null. */
    fun decrypt(ct: ByteArray): ByteArray? {
        if (ct.size < MAC_SIZE) return null
        val nonce = buildNonce(recvNonce++)
        val pt = ByteArray(ct.size - MAC_SIZE)
        val ptLen = LongArray(1)
        val rc = sodium.crypto_aead_chacha20poly1305_ietf_decrypt(
            pt, ptLen, null, ct, ct.size.toLong(), null, 0L, nonce, recvKey
        )
        if (rc != 0) return null
        return pt.copyOf(ptLen[0].toInt())
    }
}
