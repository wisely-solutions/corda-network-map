package solutions.wisely.corda.nms.controllers

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.async.DeferredResult
import solutions.wisely.corda.nms.crypto.CertificateUtils

@RestController
class CertificatesController (
    @Value(value = "\${doormanCN:CN=Corda Doorman CA}") doormanCommonName: String,
) {
    private val doormanCa: CertificateAndKeyPair = CertificateUtils.createDoormanCa(DEV_ROOT_CA, doormanCommonName)
    private val csrRequests: MutableMap<String, JcaPKCS10CertificationRequest> = ConcurrentHashMap()
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val trustRoot: ByteArray = CertificateUtils.trustStore()

    @RequestMapping(path = ["/certificate/{id}"], method = [RequestMethod.GET])
    fun getSignedCSR(@PathVariable(value = "id") id: String): ResponseEntity<ByteArray> {
        val csr = csrRequests[id]
        val issuerCert: X509Certificate = this.doormanCa.certificate
        val issuerKeyPair: KeyPair = this.doormanCa.keyPair
        if (csr != null) {
            val nodeCaCertificate: X509Certificate =
                CertificateUtils.createAndSignNodeCACerts(
                    CertificateAndKeyPair(
                        issuerCert,
                        issuerKeyPair
                    ), csr as PKCS10CertificationRequest
                )
            val backingStream = ByteArrayOutputStream()
            ZipOutputStream(backingStream).use { zipOutputStream ->
                listOf(
                    nodeCaCertificate,
                    this.doormanCa.certificate,
                    DEV_ROOT_CA.certificate
                ).forEach { certificate ->
                    val x500Principal = certificate.getSubjectX500Principal()
                    zipOutputStream.putNextEntry(ZipEntry(x500Principal.name))
                    zipOutputStream.write(certificate.encoded)
                    zipOutputStream.closeEntry()
                }
            }
            return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(
                    backingStream.toByteArray()
                )
        } else {
            return ResponseEntity.notFound().build()
        }
    }


    @RequestMapping(path = ["/certificate"], method = [RequestMethod.POST])
    fun submitCSR(@RequestBody input: ByteArray): DeferredResult<ResponseEntity<String>> {
        val csr = JcaPKCS10CertificationRequest(input)
        val id = BigInteger(128, Random()).toString(36)
        val result: DeferredResult<ResponseEntity<String>> = DeferredResult<ResponseEntity<String>>()
        executorService.submit {
            csrRequests[id] = csr
            result.setResult(ResponseEntity.ok().body(id))
        }
        return result
    }


    @RequestMapping(path = ["/truststore", "/trustStore"], method = [RequestMethod.GET])
    fun getTrustStore(): ResponseEntity<ByteArray> {
        return ResponseEntity.ok().header(
            "Content-Type",
            "application/octet-stream"
        ).header(
            "Content-Disposition",
            "attachment; filename=\"network-root-truststore.jks\""
        ).body(
            trustRoot
        )
    }
}