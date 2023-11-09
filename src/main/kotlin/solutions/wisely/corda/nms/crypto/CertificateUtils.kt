package solutions.wisely.corda.nms.crypto

import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.security.auth.x500.X500Principal
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.core.internal.toX500Name
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.toJca
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest


object CertificateUtils {
    private val provider: BouncyCastleProvider = BouncyCastleProvider()

    fun init() {
        Security.insertProviderAt(provider, 1)
    }

    fun createNetworkMapCa(rootCa: CertificateAndKeyPair, commonName: String): CertificateAndKeyPair {
        val keyPair = Crypto.generateKeyPair()
        val cert = X509Utilities.createCertificate(
            CertificateType.NETWORK_MAP,
            rootCa.certificate,
            rootCa.keyPair,
            X500Principal(commonName),
            keyPair.public)
        return CertificateAndKeyPair(cert, keyPair)
    }

    fun createDoormanCa(rootCa: CertificateAndKeyPair, commonName: String): CertificateAndKeyPair {
        val keyPair = Crypto.generateKeyPair()
        val cert = X509Utilities.createCertificate(
            CertificateType.INTERMEDIATE_CA,
            rootCa.certificate,
            rootCa.keyPair,
            X500Principal(commonName),
            keyPair.public)
        return CertificateAndKeyPair(cert, keyPair)
    }

    fun trustStore(): ByteArray {
        val out = ByteArrayOutputStream()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, null)
        keyStore.setCertificateEntry("cordarootca", DEV_ROOT_CA.certificate)
        keyStore.store(out, "trustpass".toCharArray())
        return out.toByteArray()
    }

    fun createAndSignNodeCACerts(
        caCertAndKey: CertificateAndKeyPair,
        request: PKCS10CertificationRequest
    ): X509Certificate {
        val jcaRequest = JcaPKCS10CertificationRequest(request)
        val s = jcaRequest.subject.toString()

        val nameConstraints = NameConstraints(
            arrayOf(GeneralSubtree(GeneralName(4, parse(s).toX500Name()))),
            arrayOfNulls(0)
        )

        val validityWindow = Date.from(Instant.now()) to Date.from(Instant.now().plus(500, ChronoUnit.DAYS))
        val subject = X500Principal(parse(jcaRequest.subject.toString()).toX500Name().encoded)

        val builder = X509Utilities.createPartialCertificate(
            CertificateType.NODE_CA,
            caCertAndKey.certificate.subjectX500Principal,
            caCertAndKey.keyPair.public,
            subject,
            jcaRequest.publicKey,
            validityWindow,
            nameConstraints,
            null,
            null
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(provider)
            .build(caCertAndKey.keyPair.private)

        val x509CertificateHolder = builder.build(signer)
        requireNotNull(x509CertificateHolder) { "Certificate holder must not be null" }

        val certificate = x509CertificateHolder.toJca()
        certificate.checkValidity(Date())
        certificate.verify(caCertAndKey.keyPair.public)

        return certificate
    }

}