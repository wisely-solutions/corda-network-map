package solutions.wisely.corda.nms.repository

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.SignedNodeInfo

data class NodeInfoWrapper (
    val hash: SecureHash,
    val signedNodeInfo: SignedNodeInfo,
    val byteRepresentation: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NodeInfoWrapper

        if (hash != other.hash) return false
        if (signedNodeInfo != other.signedNodeInfo) return false
        if (!byteRepresentation.contentEquals(other.byteRepresentation)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + signedNodeInfo.hashCode()
        result = 31 * result + byteRepresentation.contentHashCode()
        return result
    }
}