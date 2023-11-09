package solutions.wisely.corda.nms.repository

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import org.springframework.stereotype.Component

@Component
class NodeInfoRepository {
    private val lock = ReentrantReadWriteLock()
    private val hashToNodeMap: ConcurrentHashMap<SecureHash, NodeInfoWrapper> = ConcurrentHashMap()

    fun save(nodeInfo: SignedNodeInfo) {
        lock.write {
            hashToNodeMap[nodeInfo.raw.hash] = NodeInfoWrapper(
                nodeInfo.raw.hash,
                nodeInfo,
                nodeInfo.serialize().bytes
            )
        }
    }

    fun findByHash(hash: String): Pair<SignedNodeInfo, ByteArray>? {
        return lock.read {
            hashToNodeMap[SecureHash.parse(hash)]?.let {
                Pair(it.signedNodeInfo, it.byteRepresentation)
            }
        }
    }

    fun getAllHashes(): Collection<SecureHash> {
        return lock.read {
            hashToNodeMap.values.map { it.hash }
        }
    }

    fun clear(): Int {
        return lock.write {
            val listSize = hashToNodeMap.size
            hashToNodeMap.clear()
            listSize
        }
    }
}