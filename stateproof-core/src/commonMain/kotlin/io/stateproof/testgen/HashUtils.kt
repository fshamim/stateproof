package io.stateproof.testgen

/**
 * CRC hashing utilities for generating unique test identifiers.
 */
object HashUtils {

    /**
     * Computes CRC16-ARC checksum.
     *
     * Polynomial: 0xA001 (reversed 0x8005)
     * Initial value: 0x0000
     *
     * @param data Input byte array
     * @return 16-bit CRC value
     */
    fun crc16(data: ByteArray): Int {
        var crc = 0x0000

        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (i in 0 until 8) {
                val carry = crc and 0x0001
                crc = crc ushr 1
                if (carry != 0) {
                    crc = crc xor 0xA001
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Computes CRC32 checksum (ISO 3309 / ITU-T V.42).
     *
     * Polynomial: 0xEDB88320 (reversed 0x04C11DB7)
     * Initial value: 0xFFFFFFFF
     * Final XOR: 0xFFFFFFFF
     *
     * @param data Input byte array
     * @return 32-bit CRC value
     */
    fun crc32(data: ByteArray): Long {
        var crc = 0xFFFFFFFF

        for (byte in data) {
            crc = crc xor (byte.toLong() and 0xFF)
            for (i in 0 until 8) {
                val carry = crc and 0x00000001
                crc = crc ushr 1
                if (carry != 0L) {
                    crc = crc xor 0xEDB88320
                }
            }
        }
        return (crc xor 0xFFFFFFFF) and 0xFFFFFFFF
    }

    /**
     * Converts a 16-bit value to uppercase hex string.
     */
    fun toHex16(value: Int): String {
        return value.toString(16).uppercase().padStart(4, '0')
    }

    /**
     * Converts a 32-bit value to uppercase hex string.
     */
    fun toHex32(value: Long): String {
        return value.toString(16).uppercase().padStart(8, '0')
    }

    /**
     * Computes hash for a path string using the specified algorithm.
     *
     * @param pathString The path string to hash
     * @param algorithm The hash algorithm to use
     * @return Hex string representation of the hash
     */
    fun hashPath(pathString: String, algorithm: TestGenConfig.HashAlgorithm): String {
        val bytes = pathString.encodeToByteArray()
        return when (algorithm) {
            TestGenConfig.HashAlgorithm.CRC16 -> toHex16(crc16(bytes))
            TestGenConfig.HashAlgorithm.CRC32 -> toHex32(crc32(bytes))
        }
    }
}
