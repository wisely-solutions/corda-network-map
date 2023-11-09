package solutions.wisely.corda.nms.controllers

import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference
import net.corda.core.crypto.SecureHash.Companion.parse
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult
import solutions.wisely.corda.nms.crypto.CertificateUtils
import solutions.wisely.corda.nms.notary.NotaryInfoResolver
import solutions.wisely.corda.nms.repository.NodeInfoRepository

@RestController
@RequestMapping("/network-map")
class NetworkMapController @Autowired constructor(
    @Autowired private val nodeInfoRepository: NodeInfoRepository,
    @Autowired private val notaryInfoLoader: NotaryInfoResolver,
    @Value(value = "\${minimumPlatformVersion:1}") minPlatformVersion: String,
    @Value(value = "\${networkMapCN:CN=NetworkMap}") networkMapCommonName: String
) {
    private val networkMapCa: CertificateAndKeyPair = CertificateUtils.createNetworkMapCa(
        DEV_ROOT_CA,
        networkMapCommonName
    )
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val networkMap: AtomicReference<SerializedBytes<SignedDataWithCert<NetworkMap>>> = AtomicReference()
    private val signedNetworkParams: AtomicReference<SignedDataWithCert<NetworkParameters>> = AtomicReference()

    init {
        val networkParams = NetworkParameters(
            minimumPlatformVersion = minPlatformVersion.toInt(),
            notaries = notaryInfoLoader.resolve(),
            maxMessageSize = 10485760 * 10,
            maxTransactionSize = 10485760 * 5,
            modifiedTime = Instant.MIN,
            epoch = 10,
            whitelistedContractImplementations = emptyMap()
        )
        this.signedNetworkParams.set(
            networkParams.signWithCert(
                networkMapCa.keyPair.private,
                networkMapCa.certificate
            )
        )


        this.networkMap.set(buildNetworkMap())
    }

    private fun buildNetworkMap(): SerializedBytes<SignedDataWithCert<NetworkMap>> {
        val allNodes = this.nodeInfoRepository.getAllHashes()
        val networkMap = NetworkMap(
            allNodes.toList(),
            signedNetworkParams.get().raw.hash,
            null
        )

        return networkMap.signWithCert(
            networkMapCa.keyPair.private,
            networkMapCa.certificate
        ).serialize()
    }

    @ResponseBody
    @RequestMapping(method = [RequestMethod.GET], produces = ["application/json"], value = ["/map-stats"])
    fun stats(): SimpleStats {
        return SimpleStats(
            signedNetworkParams.get().verified().notaries.map {
                "OU=" + it.identity.name.organisationUnit +
                        " O=" + it.identity.name.organisation +
                        " L=" + it.identity.name.locality +
                        " C=" + it.identity.name.country
            },
            nodeInfoRepository.getAllHashes()
                .mapNotNull {
                    nodeInfoRepository.findByHash(it.toString())?.first?.verified()?.legalIdentities?.firstOrNull()
                }.map { it.name.organisation }
        )
    }


    @ResponseBody
    @RequestMapping(
        method = [RequestMethod.GET],
        produces = ["application/json"],
        value = ["/reset-persisted-nodes"]
    )
    fun resetPersistedNodes(): ResponseEntity<String> {
        val result = nodeInfoRepository.clear()
        return ResponseEntity<String>("Deleted : {$result} rows.", HttpStatus.ACCEPTED)
    }


    @RequestMapping(
        method = [RequestMethod.GET],
        path = ["/network-parameters/{hash}"],
        produces = ["application/octet-stream"]
    )
    fun getNetworkParams(@PathVariable(value = "hash") hash: String): ResponseEntity<ByteArray> {
        return if (parse(hash) == signedNetworkParams.get().raw.hash) {
            ResponseEntity.ok().header(
                "Cache-Control",
                "max-age=" + ThreadLocalRandom.current().nextInt(10, 30)
            ).body(
                signedNetworkParams.get().serialize().bytes
            )

        } else {
            ResponseEntity.notFound().build()
        }
    }


    @RequestMapping(method = [RequestMethod.GET])
    fun getNetworkMap(): ResponseEntity<ByteArray> {
        return if (networkMap.get() != null) {
            val networkMapBytes = networkMap.get().bytes
            ResponseEntity.ok()
                .contentLength(networkMapBytes.size.toLong())
                .contentType(
                    MediaType.APPLICATION_OCTET_STREAM
                ).header(
                    "Cache-Control",
                    "max-age=" + ThreadLocalRandom.current().nextInt(10, 30)
                )
                .body(networkMapBytes)
        } else {
            ResponseEntity.notFound().build()
        }
    }


    @RequestMapping(path = ["/node-info/{hash}"], method = [RequestMethod.GET])
    fun getNodeInfo(@PathVariable(value = "hash") input: String): ResponseEntity<ByteArray> {
        val foundNodeInfo = nodeInfoRepository.findByHash(input)
        return if (foundNodeInfo != null) {
            ResponseEntity.ok()
                .contentLength(foundNodeInfo.second.size.toLong())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(foundNodeInfo.second)
        } else ResponseEntity.notFound().build()
    }




    @RequestMapping(path = ["/publish"], method = [RequestMethod.POST])
    fun postNodeInfo(@RequestBody input: ByteArray): DeferredResult<ResponseEntity<String>> {
        val infoNode = input.deserialize<SignedNodeInfo>()
        nodeInfoRepository.save(infoNode)
        val result: DeferredResult<ResponseEntity<String>> = DeferredResult<ResponseEntity<String>>()
        executorService.submit {
            networkMap.set(buildNetworkMap())
            result.setResult(ResponseEntity.ok().body("OK"))
        }
        return result
    }


    @RequestMapping(path = ["/bumpEpoch"], method = [RequestMethod.GET])
    fun bumpEpoch() {
        val currentNetworkParams = signedNetworkParams.get().verified()
        signedNetworkParams.set(
            currentNetworkParams
                .copy(
                    epoch = currentNetworkParams.epoch + 1,
                    notaries = notaryInfoLoader.resolve()
                ).signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        )
        networkMap.set(buildNetworkMap())
    }


    @RequestMapping(path = ["/bumpMPV"], method = [RequestMethod.GET])
    fun bumpMPVInNetParams() {
        val currentNetworkParams = signedNetworkParams.get().verified()
        signedNetworkParams.set(
            currentNetworkParams
                .copy(
                    minimumPlatformVersion = currentNetworkParams.minimumPlatformVersion + 1,
                    epoch = currentNetworkParams.epoch + 1,
                    notaries = notaryInfoLoader.resolve()
                ).signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
        )
        networkMap.set(buildNetworkMap())
    }

}