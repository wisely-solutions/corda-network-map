package solutions.wisely.corda.nms.notary

import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import solutions.wisely.corda.nms.io.json
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class NotaryInfoResolver(
    @Value(value = "\${NOTARIES:notaries.json}") private val notariesConfig: String
) {
    fun resolve(): List<NotaryInfo> {
        return File(notariesConfig).json<NotariesConfig>()
            .list.map {
                val keyStore = KeyStore.getInstance("JKS")
                keyStore.load(File(it.nodeKeystoreLocation).inputStream(), it.nodeKeystorePassword.toCharArray())
                val certificate = keyStore.getCertificate("identity-private-key")
                NotaryInfo(
                    Party(
                        certificate as X509Certificate
                    ),
                    it.validating
                )
            }
    }
}

data class NotariesConfig(
    val list: List<NotaryConfig>
)

data class NotaryConfig(
    val nodeKeystoreLocation: String,
    val nodeKeystorePassword: String,
    val validating: Boolean
)