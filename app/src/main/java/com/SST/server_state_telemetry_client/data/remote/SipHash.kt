package com.SST.server_state_telemetry_client.data.remote

import kotlin.ExperimentalUnsignedTypes

@OptIn(ExperimentalUnsignedTypes::class)
object SipHash {
    private fun rotl(x: ULong, b: Int): ULong {
        return (x shl b) or (x shr (64 - b))
    }

    private fun sipRound(v: ULongArray) {
        v[0] = v[0] + v[1]
        v[1] = rotl(v[1], 13)
        v[1] = v[1] xor v[0]
        v[0] = rotl(v[0], 32)
        v[2] = v[2] + v[3]
        v[3] = rotl(v[3], 16)
        v[3] = v[3] xor v[2]
        v[0] = v[0] + v[3]
        v[3] = rotl(v[3], 21)
        v[3] = v[3] xor v[0]
        v[2] = v[2] + v[1]
        v[1] = rotl(v[1], 17)
        v[1] = v[1] xor v[2]
        v[2] = rotl(v[2], 32)
    }

    /**
     * SipHash-2-4 128비트 해시 연산을 수행합니다.
     * key: 16바이트 인증 키
     * data: 해싱할 원본 데이터 바이트 배열
     */
    fun hash128(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 16) { "SipHash 키는 반드시 16바이트여야 합니다." }

        // 키를 리틀 엔디안 64비트 정수로 로드
        val k0 = readLE64(key, 0)
        val k1 = readLE64(key, 8)

        // 초기 상태 설정
        val v = ULongArray(4)
        v[0] = 0x736f6d6570736575UL xor k0
        v[1] = 0x646f72616e646f6dUL xor k1 xor 0xeeUL
        v[2] = 0x6c7967656e657261UL xor k0
        v[3] = 0x7465646279746573UL xor k1

        val inlen = data.size
        val left = inlen % 8
        val end = inlen - left

        // 8바이트 단위 블록 순차 처리
        for (i in 0 until end step 8) {
            val mi = readLE64(data, i)
            v[3] = v[3] xor mi
            sipRound(v)
            sipRound(v)
            v[0] = v[0] xor mi
        }

        // 마지막 미완성 패딩 블록 처리
        var b = (inlen.toULong() and 0xFFUL) shl 56
        var t = 0UL
        for (i in 0 until left) {
            t = t or ((data[end + i].toULong() and 0xFFUL) shl (i * 8))
        }
        b = b or t

        v[3] = v[3] xor b
        sipRound(v)
        sipRound(v)
        v[0] = v[0] xor b

        // 파이널라이제이션 (128비트 출력용 두 단계 처리)
        v[2] = v[2] xor 0xeeUL
        sipRound(v)
        sipRound(v)
        sipRound(v)
        sipRound(v)
        val out0 = v[0] xor v[1] xor v[2] xor v[3]

        v[1] = v[1] xor 0xddUL
        sipRound(v)
        sipRound(v)
        sipRound(v)
        sipRound(v)
        val out1 = v[0] xor v[1] xor v[2] xor v[3]

        // 16바이트 결과 바이트 배열 변환
        val result = ByteArray(16)
        writeLE64(result, 0, out0)
        writeLE64(result, 8, out1)
        return result
    }

    private fun readLE64(data: ByteArray, offset: Int): ULong {
        var value = 0UL
        for (i in 0 until 8) {
            value = value or ((data[offset + i].toULong() and 0xFFUL) shl (i * 8))
        }
        return value
    }

    private fun writeLE64(dest: ByteArray, offset: Int, value: ULong) {
        for (i in 0 until 8) {
            dest[offset + i] = ((value shr (i * 8)) and 0xFFUL).toByte()
        }
    }
}
